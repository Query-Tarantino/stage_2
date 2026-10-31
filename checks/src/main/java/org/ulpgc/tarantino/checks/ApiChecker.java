package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.json.TarantinoJson;
import org.ulpgc.tarantino.common.terms.FileStopwordsLoader;
import org.ulpgc.tarantino.common.terms.Tokenizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

public class ApiChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiChecker.class);
    private static final ObjectMapper MAPPER = TarantinoJson.mapper();
    private static final int MAX_WAIT_SECONDS = 60;

    private final CheckedClient client;
    private final String frontEndUrl;
    private final String operationalUrl;
    private final Tokenizer tokenizer;
    private final List<JsonNode> sampleBooks = new ArrayList<>();

    public ApiChecker(CheckedClient client, String frontEndUrl, String operationalUrl) {
        this.client = client;
        this.frontEndUrl = trimSlash(frontEndUrl);
        this.operationalUrl = trimSlash(operationalUrl);
        Path stopwordsPath = findStopwordsPath();
        Set<String> stopwords = Files.exists(stopwordsPath)
                ? new FileStopwordsLoader(stopwordsPath).stopwords()
                : Set.of();
        this.tokenizer = new Tokenizer(stopwords);
    }

    public void checkAll(List<Integer> sampleIds, Path expectedPath) throws IOException, InterruptedException {
        if (expectedPath == null || !Files.exists(expectedPath)) {
            throw new AssertionError("Expected answers file not found at " + expectedPath);
        }
        LOGGER.info("Starting API conformance checks");
        checkHealth();
        checkNginxUnknownPaths();
        checkOperationalEndpoints();
        ensurePublishedAndRefreshed(sampleIds);
        checkStatusEndpoints();
        checkIdempotency(sampleIds);
        checkBooksEndpoints(sampleIds);
        checkFiltersSummary();
        checkSearchParametersOrder();
        checkSearchEndpoints();
        checkAgainstExpected(expectedPath);
        LOGGER.info("All API conformance checks passed successfully");
    }

    public void checkNginxUnknownPaths() throws IOException, InterruptedException {
        LOGGER.info("Checking nginx 404 Problem Details on unknown paths");
        checkUnknownPath(frontEndUrl + "/unknown-path");
        checkUnknownPath(operationalUrl + "/unknown-path");
    }

    private void checkUnknownPath(String url) throws IOException, InterruptedException {
        CheckedClient.Response response = client.getRaw(url);
        assertEquals(404, response.status());
        List<String> contentTypes = response.headers().get("Content-Type");
        if (contentTypes == null || contentTypes.isEmpty()) {
            contentTypes = response.headers().get("content-type");
        }
        assertTrue(contentTypes != null && !contentTypes.isEmpty());
        assertTrue(contentTypes.getFirst().toLowerCase().startsWith("application/problem+json"));
        assertTrue(response.json() != null);
        client.validator().validateProblem(response.json());
        assertEquals(404, response.json().get("status").asInt());
        assertTrue(response.json().has("title"));
        assertTrue(response.json().has("detail"));
        assertTrue(!response.json().has("code"));
    }

    public void checkHealth() throws IOException, InterruptedException {
        LOGGER.info("Checking /health on service ports");
        Map<String, String> serviceByPort = Map.of(
                "7001", "crawler",
                "7002", "indexer",
                "7003", "query",
                "7004", "control"
        );
        for (Map.Entry<String, String> entry : serviceByPort.entrySet()) {
            String port = entry.getKey();
            String service = entry.getValue();
            CheckedClient.Response response;
            try {
                response = client.get("http://localhost:" + port + "/health");
            } catch (IOException exception) {
                response = checkHealthViaDocker(service, port);
            }
            assertTrue(response.status() == 200 || response.status() == 503);
            assertTrue(response.json() != null && response.json().has("status"));
        }
    }

    private CheckedClient.Response checkHealthViaDocker(String service, String port) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("docker", "compose"));
        String composeFileEnv = System.getenv("COMPOSE_FILE");
        if (composeFileEnv == null || composeFileEnv.isBlank()) {
            findComposePath().ifPresent(composePath -> {
                command.add("-f");
                command.add(composePath.toString());
            });
        }
        command.addAll(List.of("exec", "-T", service, "curl", "-isS", "http://localhost:" + port + "/health"));
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("docker compose exec failed for " + service + " with exit code " + exitCode + ": " + output);
        }
        return parseCurlResponse(output, "/health");
    }

    private static Optional<Path> findComposePath() {
        List<Path> candidates = List.of(
                Paths.get("docker-compose.yml"),
                Paths.get("../docker-compose.yml"),
                Paths.get("../../docker-compose.yml")
        );
        return candidates.stream().filter(Files::exists).findFirst();
    }

    private CheckedClient.Response parseCurlResponse(String rawOutput, String path) throws IOException {
        String[] parts = rawOutput.split("\r?\n\r?\n", 2);
        String headerSection = parts[0];
        String body = parts.length > 1 ? parts[1].trim() : "";
        String[] lines = headerSection.split("\r?\n");
        int statusCode = 200;
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (index == 0) {
                String[] tokens = line.split("\\s+");
                if (tokens.length >= 2) {
                    statusCode = Integer.parseInt(tokens[1]);
                }
            } else if (!line.isEmpty()) {
                int colonIndex = line.indexOf(':');
                if (colonIndex > 0) {
                    String headerName = line.substring(0, colonIndex).trim();
                    String headerValue = line.substring(colonIndex + 1).trim();
                    headers.computeIfAbsent(headerName, key -> new ArrayList<>()).add(headerValue);
                }
            }
        }
        return client.validateAndWrap("GET", path, statusCode, headers, body);
    }

    public void checkOperationalEndpoints() throws IOException, InterruptedException {
        LOGGER.info("Checking operational status endpoints");
        CheckedClient.Response listResponse = client.get(operationalUrl + "/ingest/list");
        assertEquals(200, listResponse.status());
        assertTrue(listResponse.json().has("books"));

        CheckedClient.Response indexStatus = client.get(operationalUrl + "/index/status");
        assertEquals(200, indexStatus.status());
        assertTrue(indexStatus.json().has("generation"));
        assertTrue(indexStatus.json().has("books_indexed"));

        CheckedClient.Response controlStatus = client.get(operationalUrl + "/control/status");
        assertEquals(200, controlStatus.status());
        assertTrue(controlStatus.json().has("books"));
        assertTrue(controlStatus.json().has("queues"));
    }

    public void checkStatusEndpoints() throws IOException, InterruptedException {
        LOGGER.info("Checking status endpoints and operational errors");
        CheckedClient.Response ingestStatus200 = client.get(operationalUrl + "/ingest/status/1342");
        assertEquals(200, ingestStatus200.status());
        assertEquals(1342, ingestStatus200.json().get("book_id").asInt());

        CheckedClient.Response indexStatus200 = client.get(operationalUrl + "/index/status/1342");
        assertEquals(200, indexStatus200.status());
        assertEquals(1342, indexStatus200.json().get("book_id").asInt());

        assertProblem(operationalUrl + "/ingest/status/999999", 404, "BOOK_NOT_FOUND", null);
        assertProblem(operationalUrl + "/index/status/999999", 404, "BOOK_NOT_FOUND", null);

        assertProblem(operationalUrl + "/ingest/status/0", 400, "INVALID_PARAMETER", "book_id");
        assertProblem(operationalUrl + "/ingest/status/abc", 400, "INVALID_PARAMETER", "book_id");

        assertProblem(operationalUrl + "/index/status/0", 400, "INVALID_PARAMETER", "book_id");
        assertProblem(operationalUrl + "/index/status/abc", 400, "INVALID_PARAMETER", "book_id");

        assertPostProblem(operationalUrl + "/ingest/0", 400, "INVALID_PARAMETER", "book_id");
        assertPostProblem(operationalUrl + "/index/update/0", 400, "INVALID_PARAMETER", "book_id");
        assertPostProblem(operationalUrl + "/index/update/999999", 404, "BOOK_NOT_FOUND", null);
    }

    private record BookStatusCheck(int bookId, String statusUrl) {}

    public void ensurePublishedAndRefreshed(List<Integer> sampleIds) throws IOException, InterruptedException {
        LOGGER.info("Ensuring all sample books are published");
        List<BookStatusCheck> pendingChecks = new ArrayList<>();
        for (int bookId : sampleIds) {
            CheckedClient.Response ingestResponse = client.post(operationalUrl + "/ingest/" + bookId, "");
            assertTrue(ingestResponse.status() == 200 || ingestResponse.status() == 202);
            if (ingestResponse.status() == 202) {
                String location = ingestResponse.header("Location");
                assertTrue(location != null && !location.isBlank());
                String statusUrl = operationalUrl + (location.startsWith("/") ? location : "/" + location);
                pendingChecks.add(new BookStatusCheck(bookId, statusUrl));
            } else {
                String bookStatus = ingestResponse.json().get("status").asText();
                if (!"PUBLISHED".equals(bookStatus)) {
                    pendingChecks.add(new BookStatusCheck(bookId, operationalUrl + "/ingest/status/" + bookId));
                }
            }
        }
        for (BookStatusCheck pendingCheck : pendingChecks) {
            waitForPublished(pendingCheck.bookId(), pendingCheck.statusUrl());
        }
        waitForSnapshotRefresh(sampleIds);
    }

    private void waitForPublished(int bookId, String statusUrl) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + (MAX_WAIT_SECONDS * 1000L);
        while (System.currentTimeMillis() < deadline) {
            CheckedClient.Response response = client.get(statusUrl);
            if (response.status() == 200 && response.json() != null && response.json().has("status")) {
                String status = response.json().get("status").asText();
                if ("PUBLISHED".equals(status)) {
                    return;
                }
                if ("FAILED".equals(status)) {
                    String reason = response.json().has("last_error") ? response.json().get("last_error").asText() : "unknown";
                    throw new AssertionError("Book " + bookId + " failed ingestion: " + reason);
                }
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Timed out waiting for book " + bookId + " to reach PUBLISHED at " + statusUrl);
    }

    private void waitForSnapshotRefresh(List<Integer> sampleIds) throws IOException, InterruptedException {
        int snapshotSeconds = Integer.parseInt(System.getenv().getOrDefault("TARANTINO_SNAPSHOT_SECONDS", "5"));
        long filtersDeadline = System.currentTimeMillis() + (MAX_WAIT_SECONDS * 1000L);
        int consecutiveSuccesses = 0;
        while (System.currentTimeMillis() < filtersDeadline && consecutiveSuccesses < 10) {
            try {
                CheckedClient.Response response = client.get(frontEndUrl + "/search/filters");
                if (response.status() == 200 && response.json() != null && response.json().has("total_books")
                        && response.json().get("total_books").asInt() >= sampleIds.size()) {
                    consecutiveSuccesses++;
                } else {
                    consecutiveSuccesses = 0;
                }
            } catch (IOException exception) {
                consecutiveSuccesses = 0;
            }
            Thread.sleep(200);
        }
        if (consecutiveSuccesses < 10) {
            throw new AssertionError("Snapshot not refreshed on all replicas: search/filters did not return total_books >= "
                    + sampleIds.size() + " across 10 consecutive requests");
        }

        for (int bookId : sampleIds) {
            long bookDeadline = System.currentTimeMillis() + ((snapshotSeconds + 2) * 1000L);
            boolean visible = false;
            while (System.currentTimeMillis() < bookDeadline) {
                CheckedClient.Response response = client.get(frontEndUrl + "/books/" + bookId);
                if (response.status() == 200) {
                    visible = true;
                    break;
                }
                Thread.sleep(500);
            }
            if (!visible) {
                throw new AssertionError("Book " + bookId + " not visible in /books/" + bookId + " within snapshot refresh");
            }
        }

        long searchDeadline = System.currentTimeMillis() + ((snapshotSeconds + 2) * 1000L);
        boolean searchVisible = false;
        while (System.currentTimeMillis() < searchDeadline) {
            try {
                CheckedClient.Response response = client.get(frontEndUrl + "/search?q=pride");
                if (response.status() == 200 && response.json() != null && response.json().has("results")
                        && containsBook(response.json().get("results"), 1342)) {
                    searchVisible = true;
                    break;
                }
            } catch (IOException exception) {
            }
            Thread.sleep(200);
        }
        if (!searchVisible) {
            throw new AssertionError("Book 1342 not found by /search?q=pride within snapshot refresh");
        }
    }

    public void checkIdempotency(List<Integer> sampleIds) throws IOException, InterruptedException {
        LOGGER.info("Verifying idempotency for published sample books");
        for (int bookId : sampleIds) {
            CheckedClient.Response ingestResponse = client.post(operationalUrl + "/ingest/" + bookId, "");
            assertEquals(200, ingestResponse.status());
            assertTrue(ingestResponse.header("Location") == null);
            assertEquals("PUBLISHED", ingestResponse.json().get("status").asText());

            CheckedClient.Response updateResponse = client.post(operationalUrl + "/index/update/" + bookId, "");
            assertEquals(200, updateResponse.status());
            assertTrue(updateResponse.header("Location") == null);
            assertEquals("PUBLISHED", updateResponse.json().get("status").asText());
        }

        CheckedClient.Response listResponse = client.get(operationalUrl + "/ingest/list");
        assertEquals(200, listResponse.status());
        JsonNode booksNode = listResponse.json().get("books");
        assertTrue(booksNode != null && booksNode.isArray());
        List<Integer> ingestedBooks = StreamSupport.stream(booksNode.spliterator(), false)
                .map(JsonNode::asInt)
                .toList();
        assertEquals(ingestedBooks.size(), listResponse.json().get("count").asInt());
        assertTrue(ingestedBooks.containsAll(sampleIds));
        IntStream.range(1, ingestedBooks.size()).forEach(index ->
                assertTrue(ingestedBooks.get(index) > ingestedBooks.get(index - 1)));
    }

    public void checkBooksEndpoints(List<Integer> sampleIds) throws IOException, InterruptedException {
        LOGGER.info("Checking /books endpoints");
        sampleBooks.clear();
        for (int bookId : sampleIds) {
            CheckedClient.Response response = client.get(frontEndUrl + "/books/" + bookId);
            assertEquals(200, response.status());
            assertEquals(bookId, response.json().get("book_id").asInt());
            sampleBooks.add(response.json());
        }

        CheckedClient.Response book1342 = client.get(frontEndUrl + "/books/1342");
        assertEquals(200, book1342.status());
        assertEquals(1342, book1342.json().get("book_id").asInt());
        assertEquals("Pride and Prejudice", book1342.json().get("title").asText());
        assertEquals("Jane Austen", book1342.json().get("author").asText());
        assertEquals("en", book1342.json().get("language").asText());
        assertTrue(book1342.json().has("languages") && book1342.json().get("languages").isArray());
        assertEquals("en", book1342.json().get("languages").get(0).asText());

        CheckedClient.Response notFound = client.get(frontEndUrl + "/books/999999");
        assertEquals(404, notFound.status());
        assertEquals("BOOK_NOT_FOUND", notFound.json().get("code").asText());

        assertProblem(frontEndUrl + "/books/0", 400, "INVALID_PARAMETER", "book_id");
        assertProblem(frontEndUrl + "/books/-5", 400, "INVALID_PARAMETER", "book_id");
        assertProblem(frontEndUrl + "/books/01", 400, "INVALID_PARAMETER", "book_id");
        assertProblem(frontEndUrl + "/books/2147483648", 400, "INVALID_PARAMETER", "book_id");
        assertProblem(frontEndUrl + "/books/abc", 400, "INVALID_PARAMETER", "book_id");
    }

    public void checkFiltersSummary() throws IOException, InterruptedException {
        LOGGER.info("Checking /search/filters");
        CheckedClient.Response response = client.get(frontEndUrl + "/search/filters");
        assertEquals(200, response.status());
        JsonNode json = response.json();
        assertTrue(json.has("languages"));
        assertTrue(json.has("years"));
        assertTrue(json.has("total_books"));
    }

    public void checkSearchParametersOrder() throws IOException, InterruptedException {
        LOGGER.info("Checking parameter validation in strict order");
        assertProblem(frontEndUrl + "/search", 400, "MISSING_PARAMETER", "q");
        assertProblem(frontEndUrl + "/search?q=", 400, "MISSING_PARAMETER", "q");
        assertProblem(frontEndUrl + "/search?q=%20", 400, "MISSING_PARAMETER", "q");

        assertProblem(frontEndUrl + "/search?q=pride&author=...!", 400, "INVALID_PARAMETER", "author");

        assertProblem(frontEndUrl + "/search?q=pride&language=eng", 400, "INVALID_PARAMETER", "language");
        assertProblem(frontEndUrl + "/search?q=pride&language=E", 400, "INVALID_PARAMETER", "language");
        assertProblem(frontEndUrl + "/search?q=pride&language=12", 400, "INVALID_PARAMETER", "language");

        assertProblem(frontEndUrl + "/search?q=pride&year=98", 400, "INVALID_PARAMETER", "year");
        assertProblem(frontEndUrl + "/search?q=pride&year=19980", 400, "INVALID_PARAMETER", "year");
        assertProblem(frontEndUrl + "/search?q=pride&year=abcd", 400, "INVALID_PARAMETER", "year");

        assertProblem(frontEndUrl + "/search?q=pride&offset=-1", 400, "INVALID_PARAMETER", "offset");
        assertProblem(frontEndUrl + "/search?q=pride&offset=abc", 400, "INVALID_PARAMETER", "offset");
        assertProblem(frontEndUrl + "/search?q=pride&offset=99999999999999999999", 400, "INVALID_PARAMETER", "offset");

        assertProblem(frontEndUrl + "/search?q=pride&limit=0", 400, "INVALID_PARAMETER", "limit");
        assertProblem(frontEndUrl + "/search?q=pride&limit=101", 400, "INVALID_PARAMETER", "limit");
        assertProblem(frontEndUrl + "/search?q=pride&limit=-5", 400, "INVALID_PARAMETER", "limit");
        assertProblem(frontEndUrl + "/search?q=pride&limit=xyz", 400, "INVALID_PARAMETER", "limit");

        assertProblem(frontEndUrl + "/search?q=&author=...!&language=bad&year=bad&offset=-1&limit=0", 400, "MISSING_PARAMETER", "q");
        assertProblem(frontEndUrl + "/search?q=pride&author=...!&language=bad&year=bad&offset=-1&limit=0", 400, "INVALID_PARAMETER", "author");
        assertProblem(frontEndUrl + "/search?q=pride&author=Austen&language=bad&year=bad&offset=-1&limit=0", 400, "INVALID_PARAMETER", "language");
        assertProblem(frontEndUrl + "/search?q=pride&author=Austen&language=en&year=bad&offset=-1&limit=0", 400, "INVALID_PARAMETER", "year");
        assertProblem(frontEndUrl + "/search?q=pride&author=Austen&language=en&year=1998&offset=-1&limit=0", 400, "INVALID_PARAMETER", "offset");
    }

    public void checkSearchEndpoints() throws IOException, InterruptedException {
        LOGGER.info("Checking search endpoints");
        CheckedClient.Response single = client.get(frontEndUrl + "/search?q=pride");
        assertEquals(200, single.status());
        assertEquals("pride", single.json().get("query").asText());
        assertTrue(single.json().get("total").asInt() >= 1);
        assertTrue(containsBook(single.json().get("results"), 1342));

        CheckedClient.Response multiple = client.get(frontEndUrl + "/search?q=pride%20prejudice");
        assertEquals(200, multiple.status());
        assertEquals("pride prejudice", multiple.json().get("query").asText());
        assertTrue(multiple.json().get("total").asInt() >= 1);
        assertTrue(containsBook(multiple.json().get("results"), 1342));

        CheckedClient.Response withAuthor = client.get(frontEndUrl + "/search?q=pride&author=Austen");
        assertEquals(200, withAuthor.status());
        assertEquals("Austen", withAuthor.json().get("filters").get("author").asText());
        assertTrue(withAuthor.json().get("total").asInt() >= 1);
        assertTrue(containsBook(withAuthor.json().get("results"), 1342));

        CheckedClient.Response langEn = client.get(frontEndUrl + "/search?q=pride&language=en");
        assertEquals(200, langEn.status());
        assertEquals("en", langEn.json().get("filters").get("language").asText());
        assertTrue(langEn.json().get("total").asInt() >= 1);
        assertTrue(containsBook(langEn.json().get("results"), 1342));

        checkLanguageFilter(sampleBooks, "de");
        checkLanguageFilter(sampleBooks, "fr");

        CheckedClient.Response langZz = client.get(frontEndUrl + "/search?q=pride&language=zz");
        assertEquals(200, langZz.status());
        assertEquals("zz", langZz.json().get("filters").get("language").asText());
        assertEquals(0, langZz.json().get("total").asInt());
        assertEquals(0, langZz.json().get("results").size());

        CheckedClient.Response withYear = client.get(frontEndUrl + "/search?q=pride&year=1998");
        assertEquals(200, withYear.status());
        assertEquals(1998, withYear.json().get("filters").get("year").asInt());
        assertTrue(withYear.json().get("total").asInt() >= 1);
        assertTrue(containsBook(withYear.json().get("results"), 1342));

        CheckedClient.Response combined = client.get(frontEndUrl + "/search?q=pride&author=Austen&language=en&year=1998");
        assertEquals(200, combined.status());
        assertEquals("Austen", combined.json().get("filters").get("author").asText());
        assertEquals("en", combined.json().get("filters").get("language").asText());
        assertEquals(1998, combined.json().get("filters").get("year").asInt());
        assertTrue(combined.json().get("total").asInt() >= 1);
        assertTrue(containsBook(combined.json().get("results"), 1342));

        CheckedClient.Response page0 = client.get(frontEndUrl + "/search?q=pride&offset=0&limit=1");
        assertEquals(200, page0.status());
        assertEquals(0, page0.json().get("offset").asInt());
        assertEquals(1, page0.json().get("limit").asInt());

        CheckedClient.Response page1 = client.get(frontEndUrl + "/search?q=pride&offset=1&limit=1");
        assertEquals(200, page1.status());
        assertEquals(1, page1.json().get("offset").asInt());
        assertEquals(1, page1.json().get("limit").asInt());
        assertEquals(page0.json().get("total").asInt(), page1.json().get("total").asInt());

        if (page0.json().get("total").asInt() >= 2) {
            int firstBookId = page0.json().get("results").get(0).get("book_id").asInt();
            int secondBookId = page1.json().get("results").get(0).get("book_id").asInt();
            assertTrue(firstBookId != secondBookId);
        }

        CheckedClient.Response largeOffset = client.get(frontEndUrl + "/search?q=pride&offset=1000000000");
        assertEquals(200, largeOffset.status());
        assertEquals(1000000000, largeOffset.json().get("offset").asInt());
        assertEquals(0, largeOffset.json().get("results").size());

        CheckedClient.Response stopwords = client.get(frontEndUrl + "/search?q=the%20and");
        assertEquals(200, stopwords.status());
        assertEquals(0, stopwords.json().get("total").asInt());
        assertEquals(0, stopwords.json().get("results").size());

        CheckedClient.Response empty = client.get(frontEndUrl + "/search?q=pyromaniac%20liliput");
        assertEquals(200, empty.status());
        assertEquals(0, empty.json().get("total").asInt());
        assertEquals(0, empty.json().get("results").size());
    }

    public void checkAgainstExpected(Path expectedPath) throws IOException, InterruptedException {
        LOGGER.info("Validating responses against expected.json: {}", expectedPath);
        JsonNode expected = MAPPER.readTree(expectedPath.toFile());
        if (expected.has("searches")) {
            Iterable<Map.Entry<String, JsonNode>> searchEntries = () -> expected.get("searches").fields();
            StreamSupport.stream(searchEntries.spliterator(), false)
                    .forEach(this::checkSearchAgainstExpected);
        }
        if (expected.has("books")) {
            Iterable<Map.Entry<String, JsonNode>> bookEntries = () -> expected.get("books").fields();
            StreamSupport.stream(bookEntries.spliterator(), false)
                    .forEach(this::checkBookAgainstExpected);
        }
    }

    private void checkSearchAgainstExpected(Map.Entry<String, JsonNode> entry) {
        try {
            JsonNode searchExpected = entry.getValue();
            String path = searchExpected.has("path")
                    ? searchExpected.get("path").asText()
                    : "/search?q=" + entry.getKey().replace(" ", "%20");
            CheckedClient.Response response = client.get(frontEndUrl + path);
            assertEquals(200, response.status());
            if (searchExpected.has("total")) {
                assertEquals(searchExpected.get("total").asInt(), response.json().get("total").asInt());
            }
            if (searchExpected.has("book_ids") && searchExpected.get("book_ids").isArray()) {
                List<Integer> expectedIds = StreamSupport.stream(searchExpected.get("book_ids").spliterator(), false)
                        .map(JsonNode::asInt)
                        .toList();
                List<Integer> actualIds = StreamSupport.stream(response.json().get("results").spliterator(), false)
                        .map(resultNode -> resultNode.get("book_id").asInt())
                        .toList();
                assertEquals(expectedIds, actualIds);
            }
        } catch (IOException | InterruptedException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void checkBookAgainstExpected(Map.Entry<String, JsonNode> entry) {
        try {
            String bookIdStr = entry.getKey();
            JsonNode bookExpected = entry.getValue();
            CheckedClient.Response response = client.get(frontEndUrl + "/books/" + bookIdStr);
            assertEquals(200, response.status());
            if (bookExpected.has("title") && !bookExpected.get("title").isNull()) {
                assertEquals(bookExpected.get("title").asText(), response.json().get("title").asText());
            }
            if (bookExpected.has("author") && !bookExpected.get("author").isNull()) {
                assertEquals(bookExpected.get("author").asText(), response.json().get("author").asText());
            }
            if (bookExpected.has("language") && !bookExpected.get("language").isNull()) {
                assertEquals(bookExpected.get("language").asText(), response.json().get("language").asText());
            }
            if (bookExpected.has("year") && !bookExpected.get("year").isNull()) {
                assertEquals(bookExpected.get("year").asInt(), response.json().get("year").asInt());
            }
            if (bookExpected.has("languages") && !bookExpected.get("languages").isNull()) {
                assertEquals(bookExpected.get("languages"), response.json().get("languages"));
            }
            if (bookExpected.has("original_year")) {
                JsonNode expectedOriginalYear = bookExpected.get("original_year");
                JsonNode actualOriginalYear = response.json().get("original_year");
                if (expectedOriginalYear.isNull()) {
                    assertTrue(actualOriginalYear == null || actualOriginalYear.isNull());
                } else {
                    assertTrue(actualOriginalYear != null && !actualOriginalYear.isNull());
                    assertEquals(expectedOriginalYear.asInt(), actualOriginalYear.asInt());
                }
            }
        } catch (IOException | InterruptedException exception) {
            throw new RuntimeException(exception);
        }
    }

    public void generateExpected(List<Integer> sampleIds, Path expectedPath) throws IOException, InterruptedException {
        LOGGER.info("Generating expected answers to {}", expectedPath);
        List<Integer> targetIds = (sampleIds != null && !sampleIds.isEmpty()) ? sampleIds : readSampleIds();
        ensurePublishedAndRefreshed(targetIds);
        ObjectNode root = MAPPER.createObjectNode();
        root.put("total_books", targetIds.size());
        ArrayNode sampleIdsArray = root.putArray("sample_ids");
        targetIds.forEach(sampleIdsArray::add);

        ObjectNode searchesNode = root.putObject("searches");
        ObjectNode booksNode = root.putObject("books");
        List<JsonNode> bookNodes = new ArrayList<>();
        targetIds.forEach(bookId -> {
            try {
                CheckedClient.Response response = client.get(frontEndUrl + "/books/" + bookId);
                if (response.status() == 200 && response.json() != null) {
                    ObjectNode bookNode = booksNode.putObject(String.valueOf(bookId));
                    bookNode.put("book_id", bookId);
                    bookNode.set("title", response.json().get("title"));
                    bookNode.set("author", response.json().get("author"));
                    bookNode.set("language", response.json().get("language"));
                    bookNode.set("year", response.json().get("year"));
                    if (response.json().has("languages")) {
                        bookNode.set("languages", response.json().get("languages"));
                    }
                    bookNode.set("original_year", response.json().get("original_year"));
                    bookNodes.add(response.json());
                }
            } catch (IOException | InterruptedException exception) {
                throw new RuntimeException(exception);
            }
        });

        Optional<JsonNode> deBook = bookNodes.stream()
                .filter(book -> book.has("languages") && StreamSupport.stream(book.get("languages").spliterator(), false)
                        .anyMatch(langNode -> "de".equalsIgnoreCase(langNode.asText())))
                .findFirst();
        String deTerm = deBook.map(this::pickSearchTerm).orElse("faust");

        Optional<JsonNode> frBook = bookNodes.stream()
                .filter(book -> book.has("languages") && StreamSupport.stream(book.get("languages").spliterator(), false)
                        .anyMatch(langNode -> "fr".equalsIgnoreCase(langNode.asText())))
                .findFirst();
        String frTerm = frBook.map(this::pickSearchTerm).orElse("monde");

        record SearchCase(String name, String path) {}
        List<SearchCase> searchCases = List.of(
                new SearchCase("single_word", "/search?q=pride"),
                new SearchCase("multi_word", "/search?q=pride%20prejudice"),
                new SearchCase("filter_author", "/search?q=pride&author=Austen"),
                new SearchCase("filter_language", "/search?q=pride&language=en"),
                new SearchCase("filter_language_de", "/search?q=" + deTerm + "&language=de"),
                new SearchCase("filter_language_fr", "/search?q=" + frTerm + "&language=fr"),
                new SearchCase("filter_language_zz", "/search?q=pride&language=zz"),
                new SearchCase("filter_year", "/search?q=pride&year=1998"),
                new SearchCase("combined_filter", "/search?q=pride&author=Austen&language=en&year=1998"),
                new SearchCase("pagination_page0", "/search?q=pride&offset=0&limit=1"),
                new SearchCase("pagination_page1", "/search?q=pride&offset=1&limit=1"),
                new SearchCase("pagination_large_offset", "/search?q=pride&offset=1000000000"),
                new SearchCase("stopwords_only", "/search?q=the%20and"),
                new SearchCase("empty_results", "/search?q=pyromaniac%20liliput")
        );
        searchCases.forEach(searchCase -> {
            try {
                CheckedClient.Response response = client.get(frontEndUrl + searchCase.path());
                ObjectNode queryNode = searchesNode.putObject(searchCase.name());
                queryNode.put("path", searchCase.path());
                int total = response.json().get("total").asInt();
                queryNode.put("total", total);
                ArrayNode bookIdsArray = queryNode.putArray("book_ids");
                if (response.json().has("results") && response.json().get("results").isArray()) {
                    StreamSupport.stream(response.json().get("results").spliterator(), false)
                            .map(resultNode -> resultNode.get("book_id").asInt())
                            .forEach(bookIdsArray::add);
                }
            } catch (IOException | InterruptedException exception) {
                throw new RuntimeException(exception);
            }
        });

        if (expectedPath.getParent() != null) {
            Files.createDirectories(expectedPath.getParent());
        }
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(expectedPath.toFile(), root);
        LOGGER.info("Generated expected.json with {} books and {} queries", booksNode.size(), searchesNode.size());
    }

    private void checkLanguageFilter(List<JsonNode> books, String language) throws IOException, InterruptedException {
        Optional<JsonNode> match = books.stream()
                .filter(book -> book.has("languages") && book.get("languages").isArray())
                .filter(book -> StreamSupport.stream(book.get("languages").spliterator(), false)
                        .anyMatch(langNode -> language.equalsIgnoreCase(langNode.asText())))
                .findFirst();
        if (match.isPresent()) {
            JsonNode book = match.get();
            String term = pickSearchTerm(book);
            int bookId = book.get("book_id").asInt();
            CheckedClient.Response response = client.get(frontEndUrl + "/search?q=" + term + "&language=" + language);
            assertEquals(200, response.status());
            assertEquals(language, response.json().get("filters").get("language").asText());
            assertTrue(response.json().get("total").asInt() >= 1);
            assertTrue(containsBook(response.json().get("results"), bookId));
        }
    }

    private String pickSearchTerm(JsonNode book) {
        if (book.has("title") && !book.get("title").isNull()) {
            List<String> terms = tokenizer.distinctTerms(book.get("title").asText());
            Optional<String> descriptive = terms.stream().filter(term -> term.length() > 2).findFirst();
            if (descriptive.isPresent()) {
                return descriptive.get();
            }
            if (!terms.isEmpty()) {
                return terms.getFirst();
            }
        }
        if (book.has("author") && !book.get("author").isNull()) {
            List<String> terms = tokenizer.distinctTerms(book.get("author").asText());
            Optional<String> descriptive = terms.stream().filter(term -> term.length() > 2).findFirst();
            if (descriptive.isPresent()) {
                return descriptive.get();
            }
            if (!terms.isEmpty()) {
                return terms.getFirst();
            }
        }
        return "pride";
    }

    private static Path findStopwordsPath() {
        List<Path> candidates = List.of(
                Paths.get("workload/stopwords.txt"),
                Paths.get("../workload/stopwords.txt"),
                Paths.get("../../workload/stopwords.txt")
        );
        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElse(Paths.get("workload/stopwords.txt"));
    }

    private static List<Integer> readSampleIds() {
        Path path = findSampleIdsPath();
        try {
            return Files.readAllLines(path).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .map(Integer::parseInt)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read sample_ids from " + path, exception);
        }
    }

    private static Path findSampleIdsPath() {
        List<Path> candidates = List.of(
                Paths.get("workload/sample_ids.txt"),
                Paths.get("../workload/sample_ids.txt"),
                Paths.get("../../workload/sample_ids.txt")
        );
        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElse(Paths.get("workload/sample_ids.txt"));
    }

    private static boolean containsBook(JsonNode resultsNode, int bookId) {
        if (resultsNode == null || !resultsNode.isArray()) {
            return false;
        }
        return StreamSupport.stream(resultsNode.spliterator(), false)
                .filter(resultNode -> resultNode.has("book_id"))
                .anyMatch(resultNode -> resultNode.get("book_id").asInt() == bookId);
    }

    private void assertProblem(String url, int expectedStatus, String expectedCode, String expectedParam) throws IOException, InterruptedException {
        CheckedClient.Response response = client.get(url);
        assertEquals(expectedStatus, response.status());
        if (expectedCode != null) {
            assertTrue(response.json() != null && response.json().has("code"));
            assertEquals(expectedCode, response.json().get("code").asText());
        }
        if (expectedParam != null) {
            assertTrue(response.json() != null && response.json().has("parameter"));
            assertEquals(expectedParam, response.json().get("parameter").asText());
        }
    }

    private void assertPostProblem(String url, int expectedStatus, String expectedCode, String expectedParam) throws IOException, InterruptedException {
        CheckedClient.Response response = client.post(url, "");
        assertEquals(expectedStatus, response.status());
        if (expectedCode != null) {
            assertTrue(response.json() != null && response.json().has("code"));
            assertEquals(expectedCode, response.json().get("code").asText());
        }
        if (expectedParam != null) {
            assertTrue(response.json() != null && response.json().has("parameter"));
            assertEquals(expectedParam, response.json().get("parameter").asText());
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Assertion condition was false");
        }
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
