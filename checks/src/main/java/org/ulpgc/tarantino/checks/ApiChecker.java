package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.json.TarantinoJson;
import org.ulpgc.tarantino.common.terms.FileStopwordsLoader;
import org.ulpgc.tarantino.common.terms.Tokenizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;

/** The executable checks of the HTTP API (SPEC §6) against the running stack, over the 20 books of {@code workload/sample_ids.txt} and {@code checks/expected.json}. */
public class ApiChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiChecker.class);
    private static final ObjectMapper MAPPER = TarantinoJson.mapper();
    private static final int MAX_WAIT_SECONDS = 60;
    private static final int PRIDE_AND_PREJUDICE = 1342;
    private static final int LARGE_OFFSET = 1_000_000_000;

    private final CheckedClient client;
    private final String frontEndUrl;
    private final String operationalUrl;
    private final Tokenizer tokenizer;
    private final List<JsonNode> sampleBooks = new ArrayList<>();
    private final HealthChecks health;
    private final EntryPortChecks entryPorts;
    private final OperationalChecks operational;
    private final BookChecks books;
    private final SearchValidationChecks searchValidation;

    public ApiChecker(CheckedClient client, String frontEndUrl, String operationalUrl) {
        this.client = client;
        this.frontEndUrl = trimSlash(frontEndUrl);
        this.operationalUrl = trimSlash(operationalUrl);
        Path stopwordsPath = findStopwordsPath();
        Set<String> stopwords = Files.exists(stopwordsPath)
                ? new FileStopwordsLoader(stopwordsPath).stopwords()
                : Set.of();
        this.tokenizer = new Tokenizer(stopwords);
        ProblemChecks problems = new ProblemChecks(client);
        this.health = new HealthChecks(client);
        this.entryPorts = new EntryPortChecks(client, this.frontEndUrl, this.operationalUrl);
        this.operational = new OperationalChecks(client, problems, this.operationalUrl);
        this.books = new BookChecks(client, problems, this.frontEndUrl);
        this.searchValidation = new SearchValidationChecks(problems, this.frontEndUrl);
    }

    public void checkAll(List<Integer> sampleIds, Path expectedPath) throws IOException, InterruptedException {
        if (expectedPath == null || !Files.exists(expectedPath)) {
            throw new AssertionError("Expected answers file not found at " + expectedPath);
        }
        LOGGER.info("Starting API conformance checks");
        health.check();
        entryPorts.check();
        operational.checkStatusPages();
        ensurePublishedAndRefreshed(sampleIds);
        operational.checkBookStatus();
        operational.checkIdempotency(sampleIds);
        sampleBooks.clear();
        sampleBooks.addAll(books.check(sampleIds));
        checkFiltersSummary();
        searchValidation.check();
        checkSearchEndpoints();
        checkAgainstExpected(expectedPath);
        LOGGER.info("All API conformance checks passed successfully");
    }

    private record BookStatusCheck(int bookId, String statusUrl) {}

    public void ensurePublishedAndRefreshed(List<Integer> sampleIds) throws IOException, InterruptedException {
        LOGGER.info("Ensuring all sample books are published");
        List<BookStatusCheck> pendingChecks = new ArrayList<>();
        for (int bookId : sampleIds) {
            CheckedClient.Response ingestResponse = client.post(operationalUrl + "/ingest/" + bookId, "");
            assertTrue(ingestResponse.status() == 200 || ingestResponse.status() == 202, "POST /ingest/" + bookId + " answers 200 or 202");
            if (ingestResponse.status() == 202) {
                String location = ingestResponse.header("Location");
                assertTrue(location != null && !location.isBlank(), "the 202 of POST /ingest/" + bookId + " has a Location");
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
        long refreshMillis = (Integer.parseInt(System.getenv().getOrDefault(TarantinoConfig.SNAPSHOT_SECONDS,
                String.valueOf(TarantinoConfig.DEFAULT_SNAPSHOT_SECONDS))) + 2) * 1000L;
        waitForEveryReplicaToCount(sampleIds.size());
        for (int bookId : sampleIds) {
            waitForBook(bookId, refreshMillis);
        }
        waitForSearchToFindPrideAndPrejudice(refreshMillis);
    }

    private void waitForEveryReplicaToCount(int books) throws InterruptedException {
        long filtersDeadline = System.currentTimeMillis() + (MAX_WAIT_SECONDS * 1000L);
        int consecutiveSuccesses = 0;
        while (System.currentTimeMillis() < filtersDeadline && consecutiveSuccesses < 10) {
            consecutiveSuccesses = countsEveryBook(books) ? consecutiveSuccesses + 1 : 0;
            Thread.sleep(200);
        }
        if (consecutiveSuccesses < 10) {
            throw new AssertionError("Snapshot not refreshed on all replicas: search/filters did not return total_books >= "
                    + books + " across 10 consecutive requests");
        }
    }

    private boolean countsEveryBook(int books) throws InterruptedException {
        try {
            CheckedClient.Response response = client.get(frontEndUrl + "/search/filters");
            return response.status() == 200 && response.json() != null && response.json().has("total_books")
                    && response.json().get("total_books").asInt() >= books;
        } catch (IOException exception) {
            return false;
        }
    }

    private void waitForBook(int bookId, long refreshMillis) throws IOException, InterruptedException {
        long bookDeadline = System.currentTimeMillis() + refreshMillis;
        while (System.currentTimeMillis() < bookDeadline) {
            if (client.get(frontEndUrl + "/books/" + bookId).status() == 200) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Book " + bookId + " not visible in /books/" + bookId + " within snapshot refresh");
    }

    private void waitForSearchToFindPrideAndPrejudice(long refreshMillis) throws InterruptedException {
        long searchDeadline = System.currentTimeMillis() + refreshMillis;
        while (System.currentTimeMillis() < searchDeadline) {
            if (searchFindsPrideAndPrejudice()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Book 1342 not found by /search?q=pride within snapshot refresh");
    }

    private boolean searchFindsPrideAndPrejudice() throws InterruptedException {
        try {
            CheckedClient.Response response = client.get(frontEndUrl + "/search?q=pride");
            return response.status() == 200 && response.json() != null && response.json().has("results")
                    && SearchResults.containsBook(response.json().get("results"), PRIDE_AND_PREJUDICE);
        } catch (IOException exception) {
            return false;
        }
    }

    public void checkFiltersSummary() throws IOException, InterruptedException {
        LOGGER.info("Checking /search/filters");
        CheckedClient.Response response = client.get(frontEndUrl + "/search/filters");
        assertEquals(200, response.status());
        JsonNode json = response.json();
        assertTrue(json.has("languages"), "/search/filters has languages");
        assertTrue(json.has("years"), "/search/filters has years");
        assertTrue(json.has("total_books"), "/search/filters has total_books");
    }

    public void checkSearchEndpoints() throws IOException, InterruptedException {
        LOGGER.info("Checking search endpoints");
        checkWordSearches();
        checkFilteredSearches();
        checkPagination();
        checkSearchesWithoutResults();
    }

    private void checkWordSearches() throws IOException, InterruptedException {
        assertEquals("pride", findsPrideAndPrejudice("/search?q=pride").get("query").asText());
        assertEquals("pride prejudice", findsPrideAndPrejudice("/search?q=pride%20prejudice").get("query").asText());
    }

    private void checkFilteredSearches() throws IOException, InterruptedException {
        assertEquals("Austen", findsPrideAndPrejudice("/search?q=pride&author=Austen").get("filters").get("author").asText());
        assertEquals("en", findsPrideAndPrejudice("/search?q=pride&language=en").get("filters").get("language").asText());
        checkLanguageFilter(sampleBooks, "de");
        checkLanguageFilter(sampleBooks, "fr");
        assertEquals("zz", findsNothing("/search?q=pride&language=zz").get("filters").get("language").asText());
        assertEquals(1998, findsPrideAndPrejudice("/search?q=pride&year=1998").get("filters").get("year").asInt());
        JsonNode combined = findsPrideAndPrejudice("/search?q=pride&author=Austen&language=en&year=1998").get("filters");
        assertEquals("Austen", combined.get("author").asText());
        assertEquals("en", combined.get("language").asText());
        assertEquals(1998, combined.get("year").asInt());
    }

    private void checkPagination() throws IOException, InterruptedException {
        JsonNode page0 = page("/search?q=pride&offset=0&limit=1", 0);
        JsonNode page1 = page("/search?q=pride&offset=1&limit=1", 1);
        assertEquals(page0.get("total").asInt(), page1.get("total").asInt());
        if (page0.get("total").asInt() >= 2) {
            assertTrue(page0.get("results").get(0).get("book_id").asInt() != page1.get("results").get(0).get("book_id").asInt(),
                    "pages 0 and 1 of /search?q=pride start with different books");
        }
        CheckedClient.Response largeOffset = client.get(frontEndUrl + "/search?q=pride&offset=" + LARGE_OFFSET);
        assertEquals(200, largeOffset.status());
        assertEquals(LARGE_OFFSET, largeOffset.json().get("offset").asInt());
        assertEquals(0, largeOffset.json().get("results").size());
    }

    private JsonNode page(String path, int offset) throws IOException, InterruptedException {
        CheckedClient.Response response = client.get(frontEndUrl + path);
        assertEquals(200, response.status());
        assertEquals(offset, response.json().get("offset").asInt());
        assertEquals(1, response.json().get("limit").asInt());
        return response.json();
    }

    private void checkSearchesWithoutResults() throws IOException, InterruptedException {
        findsNothing("/search?q=the%20and");
        findsNothing("/search?q=pyromaniac%20liliput");
    }

    private JsonNode findsPrideAndPrejudice(String path) throws IOException, InterruptedException {
        CheckedClient.Response response = client.get(frontEndUrl + path);
        assertEquals(200, response.status());
        assertTrue(response.json().get("total").asInt() >= 1, path + " finds at least one book");
        assertTrue(SearchResults.containsBook(response.json().get("results"), PRIDE_AND_PREJUDICE), path + " finds book 1342");
        return response.json();
    }

    private JsonNode findsNothing(String path) throws IOException, InterruptedException {
        CheckedClient.Response response = client.get(frontEndUrl + path);
        assertEquals(200, response.status());
        assertEquals(0, response.json().get("total").asInt());
        assertEquals(0, response.json().get("results").size());
        return response.json();
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
            assertEquals(200, response.status(), "the status of expected search " + entry.getKey() + " (" + path + ")");
            if (searchExpected.has("total")) {
                assertEquals(searchExpected.get("total").asInt(), response.json().get("total").asInt(),
                        "the total of expected search " + entry.getKey() + " (" + path + ")");
            }
            if (searchExpected.has("book_ids") && searchExpected.get("book_ids").isArray()) {
                List<Integer> expectedIds = StreamSupport.stream(searchExpected.get("book_ids").spliterator(), false)
                        .map(JsonNode::asInt)
                        .toList();
                List<Integer> actualIds = StreamSupport.stream(response.json().get("results").spliterator(), false)
                        .map(resultNode -> resultNode.get("book_id").asInt())
                        .toList();
                assertEquals(expectedIds, actualIds, "the book_ids of expected search " + entry.getKey() + " (" + path + ")");
            }
        } catch (IOException | InterruptedException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void checkBookAgainstExpected(Map.Entry<String, JsonNode> entry) {
        try {
            JsonNode bookExpected = entry.getValue();
            String bookId = entry.getKey();
            CheckedClient.Response response = client.get(frontEndUrl + "/books/" + bookId);
            assertEquals(200, response.status(), "the status of expected book /books/" + bookId);
            List.of("title", "author", "language").forEach(field -> assertSameText(bookId, bookExpected, response.json(), field));
            if (isPresent(bookExpected, "year")) {
                assertEquals(bookExpected.get("year").asInt(), response.json().get("year").asInt(), "the year of /books/" + bookId);
            }
            if (isPresent(bookExpected, "languages")) {
                assertEquals(bookExpected.get("languages"), response.json().get("languages"), "the languages of /books/" + bookId);
            }
            if (bookExpected.has("original_year")) {
                assertSameOriginalYear(bookId, bookExpected.get("original_year"), response.json().get("original_year"));
            }
        } catch (IOException | InterruptedException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static boolean isPresent(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull();
    }

    private static void assertSameText(String bookId, JsonNode expected, JsonNode actual, String field) {
        if (isPresent(expected, field)) {
            assertEquals(expected.get(field).asText(), actual.get(field).asText(), "the " + field + " of /books/" + bookId);
        }
    }

    private static void assertSameOriginalYear(String bookId, JsonNode expectedOriginalYear, JsonNode actualOriginalYear) {
        if (expectedOriginalYear.isNull()) {
            assertTrue(actualOriginalYear == null || actualOriginalYear.isNull(), "/books/" + bookId + " has a null original_year");
            return;
        }
        assertTrue(actualOriginalYear != null && !actualOriginalYear.isNull(), "/books/" + bookId + " has an original_year");
        assertEquals(expectedOriginalYear.asInt(), actualOriginalYear.asInt(), "the original_year of /books/" + bookId);
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
        List<JsonNode> bookNodes = expectedBooks(targetIds, booksNode);
        searchCases(termInLanguage(bookNodes, "de", "faust"), termInLanguage(bookNodes, "fr", "monde"))
                .forEach(searchCase -> expectedSearch(searchesNode, searchCase));
        if (expectedPath.getParent() != null) {
            Files.createDirectories(expectedPath.getParent());
        }
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(expectedPath.toFile(), root);
        LOGGER.info("Generated expected.json with {} books and {} queries", booksNode.size(), searchesNode.size());
    }

    private List<JsonNode> expectedBooks(List<Integer> bookIds, ObjectNode booksNode) {
        List<JsonNode> bookNodes = new ArrayList<>();
        bookIds.forEach(bookId -> {
            try {
                CheckedClient.Response response = client.get(frontEndUrl + "/books/" + bookId);
                if (response.status() == 200 && response.json() != null) {
                    expectedBook(booksNode.putObject(String.valueOf(bookId)), bookId, response.json());
                    bookNodes.add(response.json());
                }
            } catch (IOException | InterruptedException exception) {
                throw new RuntimeException(exception);
            }
        });
        return bookNodes;
    }

    private static void expectedBook(ObjectNode bookNode, int bookId, JsonNode book) {
        bookNode.put("book_id", bookId);
        List.of("title", "author", "language", "year").forEach(field -> bookNode.set(field, book.get(field)));
        if (book.has("languages")) {
            bookNode.set("languages", book.get("languages"));
        }
        bookNode.set("original_year", book.get("original_year"));
    }

    private String termInLanguage(List<JsonNode> books, String language, String otherwise) {
        return books.stream()
                .filter(book -> book.has("languages") && StreamSupport.stream(book.get("languages").spliterator(), false)
                        .anyMatch(langNode -> language.equalsIgnoreCase(langNode.asText())))
                .findFirst()
                .map(this::pickSearchTerm)
                .orElse(otherwise);
    }

    private record SearchCase(String name, String path) {
    }

    private static List<SearchCase> searchCases(String deTerm, String frTerm) {
        return List.of(
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
    }

    private void expectedSearch(ObjectNode searchesNode, SearchCase searchCase) {
        try {
            CheckedClient.Response response = client.get(frontEndUrl + searchCase.path());
            ObjectNode queryNode = searchesNode.putObject(searchCase.name());
            queryNode.put("path", searchCase.path());
            queryNode.put("total", response.json().get("total").asInt());
            ArrayNode bookIdsArray = queryNode.putArray("book_ids");
            if (response.json().has("results") && response.json().get("results").isArray()) {
                StreamSupport.stream(response.json().get("results").spliterator(), false)
                        .map(resultNode -> resultNode.get("book_id").asInt())
                        .forEach(bookIdsArray::add);
            }
        } catch (IOException | InterruptedException exception) {
            throw new RuntimeException(exception);
        }
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
            assertTrue(response.json().get("total").asInt() >= 1, "/search?q=" + term + "&language=" + language + " finds at least one book");
            assertTrue(SearchResults.containsBook(response.json().get("results"), bookId), "/search?q=" + term + "&language=" + language + " finds book " + bookId);
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

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String what) {
        if (!expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError("Check failed: " + what);
        }
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
