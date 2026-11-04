package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.ulpgc.tarantino.checks.Assertions.assertEquals;
import static org.ulpgc.tarantino.checks.Assertions.assertTrue;

public class ApiChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiChecker.class);
    private static final ObjectMapper MAPPER = TarantinoJson.mapper();

    private final CheckedClient client;
    private final String frontEndUrl;
    private final String operationalUrl;
    private final SearchTerms terms;
    private final HealthChecks health;
    private final NginxChecks nginx;
    private final OperationalChecks operational;
    private final PublicationWait publication;
    private final BookChecks books;
    private final SearchParameterChecks parameters;
    private final SearchChecks searches;

    public ApiChecker(CheckedClient client, String frontEndUrl, String operationalUrl) {
        Api api = Api.of(client, frontEndUrl, operationalUrl);
        this.client = client;
        this.frontEndUrl = api.frontEndUrl();
        this.operationalUrl = api.operationalUrl();
        this.health = new HealthChecks(client);
        this.nginx = new NginxChecks(api);
        this.operational = new OperationalChecks(api);
        this.publication = new PublicationWait(api);
        this.terms = SearchTerms.fromRepository();
        this.books = new BookChecks(api);
        this.parameters = new SearchParameterChecks(api);
        this.searches = new SearchChecks(api, terms);
    }

    public void checkAll(List<Integer> sampleIds, Path expectedPath) throws IOException, InterruptedException {
        if (expectedPath == null || !Files.exists(expectedPath)) {
            throw new AssertionError("Expected answers file not found at " + expectedPath);
        }
        LOGGER.info("Starting API conformance checks");
        health.check();
        nginx.check();
        operational.checkSummaries();
        publication.ensurePublished(sampleIds);
        operational.checkBookStatuses();
        operational.checkIdempotency(sampleIds);
        List<JsonNode> sampleBooks = books.check(sampleIds);
        searches.checkFilterSummary();
        parameters.check();
        searches.check(sampleBooks);
        checkAgainstExpected(expectedPath);
        LOGGER.info("All API conformance checks passed successfully");
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
        publication.ensurePublished(targetIds);
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

        String deTerm = SearchTerms.firstIn(bookNodes, "de").map(terms::termFor).orElse("faust");
        String frTerm = SearchTerms.firstIn(bookNodes, "fr").map(terms::termFor).orElse("monde");

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

}
