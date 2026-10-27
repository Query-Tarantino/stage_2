package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

public class ApiChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiChecker.class);
    private static final int MAX_WAIT_SECONDS = 60;

    private final CheckedClient client;
    private final String frontEndUrl;
    private final String operationalUrl;
    private final List<JsonNode> sampleBooks = new ArrayList<>();

    public ApiChecker(CheckedClient client, String frontEndUrl, String operationalUrl) {
        this.client = client;
        this.frontEndUrl = trimSlash(frontEndUrl);
        this.operationalUrl = trimSlash(operationalUrl);
    }

    public void checkAll(List<Integer> sampleIds) throws IOException, InterruptedException {
        LOGGER.info("Starting API conformance checks");
        checkHealth();
        checkOperationalEndpoints();
        ensurePublishedAndRefreshed(sampleIds);
        checkStatusEndpoints();
        checkIdempotency(sampleIds);
        checkBooksEndpoints(sampleIds);
        checkFiltersSummary();
        checkSearchParametersOrder();
        checkSearchEndpoints();
        LOGGER.info("All API conformance checks passed successfully");
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
        ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "compose", "exec", "-T", service, "curl", "-isS", "http://localhost:" + port + "/health"
        );
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("docker compose exec failed for " + service + " with exit code " + exitCode + ": " + output);
        }
        return parseCurlResponse(output, "/health");
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

    public void ensurePublishedAndRefreshed(List<Integer> sampleIds) throws IOException, InterruptedException {
        LOGGER.info("Ensuring all sample books are published");
        for (int bookId : sampleIds) {
            CheckedClient.Response ingestResponse = client.post(operationalUrl + "/ingest/" + bookId, "");
            assertTrue(ingestResponse.status() == 200 || ingestResponse.status() == 202);
            if (ingestResponse.status() == 202) {
                String location = ingestResponse.header("Location");
                String statusPath = (location != null && !location.isBlank()) ? location : "/ingest/status/" + bookId;
                String statusUrl = operationalUrl + (statusPath.startsWith("/") ? statusPath : "/" + statusPath);
                waitForPublished(bookId, statusUrl);
            } else {
                String bookStatus = ingestResponse.json().get("status").asText();
                if (!"PUBLISHED".equals(bookStatus)) {
                    waitForPublished(bookId, operationalUrl + "/ingest/status/" + bookId);
                }
            }
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
    }

    public void checkIdempotency(List<Integer> sampleIds) throws IOException, InterruptedException {
        LOGGER.info("Verifying idempotency for published sample books");
        for (int bookId : sampleIds) {
            CheckedClient.Response ingestResponse = client.post(operationalUrl + "/ingest/" + bookId, "");
            assertEquals(200, ingestResponse.status());
            assertEquals("PUBLISHED", ingestResponse.json().get("status").asText());

            CheckedClient.Response updateResponse = client.post(operationalUrl + "/index/update/" + bookId, "");
            assertEquals(200, updateResponse.status());
            assertEquals("PUBLISHED", updateResponse.json().get("status").asText());
        }

        CheckedClient.Response listResponse = client.get(operationalUrl + "/ingest/list");
        assertEquals(200, listResponse.status());
        JsonNode booksNode = listResponse.json().get("books");
        assertTrue(booksNode != null && booksNode.isArray());
        List<Integer> ingestedBooks = new ArrayList<>();
        for (JsonNode bookNode : booksNode) {
            ingestedBooks.add(bookNode.asInt());
        }
        for (int bookId : sampleIds) {
            assertTrue(ingestedBooks.contains(bookId));
        }
        for (int index = 1; index < ingestedBooks.size(); index++) {
            assertTrue(ingestedBooks.get(index) > ingestedBooks.get(index - 1));
        }
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

    private static String pickSearchTerm(JsonNode book) {
        if (book.has("title") && !book.get("title").isNull()) {
            String title = book.get("title").asText();
            for (String word : title.split("[^a-zA-Z0-9]+")) {
                if (word.length() >= 4) {
                    return word.toLowerCase();
                }
            }
        }
        if (book.has("author") && !book.get("author").isNull()) {
            String author = book.get("author").asText();
            for (String word : author.split("[^a-zA-Z0-9]+")) {
                if (word.length() >= 4) {
                    return word.toLowerCase();
                }
            }
        }
        return "the";
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
        if (expectedCode != null && response.json() != null && response.json().has("code")) {
            assertEquals(expectedCode, response.json().get("code").asText());
        }
        if (expectedParam != null && response.json() != null && response.json().has("parameter")) {
            assertEquals(expectedParam, response.json().get("parameter").asText());
        }
    }

    private void assertPostProblem(String url, int expectedStatus, String expectedCode, String expectedParam) throws IOException, InterruptedException {
        CheckedClient.Response response = client.post(url, "");
        assertEquals(expectedStatus, response.status());
        if (expectedCode != null && response.json() != null && response.json().has("code")) {
            assertEquals(expectedCode, response.json().get("code").asText());
        }
        if (expectedParam != null && response.json() != null && response.json().has("parameter")) {
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