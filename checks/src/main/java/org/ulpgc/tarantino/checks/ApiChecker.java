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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

/** The executable checks of the HTTP API (SPEC §6) against the running stack, over the 20 books of {@code workload/sample_ids.txt} and {@code checks/expected.json}. */
public class ApiChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiChecker.class);
    private static final ObjectMapper MAPPER = TarantinoJson.mapper();

    private final CheckedClient client;
    private final String frontEndUrl;
    private final String operationalUrl;
    private final SearchTerms terms;
    private final HealthChecks health;
    private final EntryPortChecks entryPorts;
    private final OperationalChecks operational;
    private final SampleIngestion ingestion;
    private final SnapshotRefreshWait snapshots;
    private final BookChecks books;
    private final SearchValidationChecks searchValidation;
    private final SearchChecks searches;
    private final SearchPageChecks pages;
    private final ExpectedAnswers expectedAnswers;

    public ApiChecker(CheckedClient client, String frontEndUrl, String operationalUrl) {
        this.client = client;
        this.frontEndUrl = trimSlash(frontEndUrl);
        this.operationalUrl = trimSlash(operationalUrl);
        Path stopwordsPath = findStopwordsPath();
        Set<String> stopwords = Files.exists(stopwordsPath)
                ? new FileStopwordsLoader(stopwordsPath).stopwords()
                : Set.of();
        this.terms = new SearchTerms(new Tokenizer(stopwords));
        ProblemChecks problems = new ProblemChecks(client);
        this.health = new HealthChecks(client);
        this.entryPorts = new EntryPortChecks(client, this.frontEndUrl, this.operationalUrl);
        this.operational = new OperationalChecks(client, problems, this.operationalUrl);
        this.ingestion = new SampleIngestion(client, this.operationalUrl);
        this.snapshots = new SnapshotRefreshWait(client, this.frontEndUrl);
        this.books = new BookChecks(client, problems, this.frontEndUrl);
        this.searchValidation = new SearchValidationChecks(problems, this.frontEndUrl);
        SearchAnswers answers = new SearchAnswers(client, this.frontEndUrl);
        this.searches = new SearchChecks(client, answers, terms, this.frontEndUrl);
        this.pages = new SearchPageChecks(answers);
        this.expectedAnswers = new ExpectedAnswers(client, this.frontEndUrl);
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
        List<JsonNode> sampleBooks = books.check(sampleIds);
        searches.checkFilters();
        searchValidation.check();
        searches.check(sampleBooks);
        pages.check();
        expectedAnswers.check(expectedPath);
        LOGGER.info("All API conformance checks passed successfully");
    }

    public void ensurePublishedAndRefreshed(List<Integer> sampleIds) throws IOException, InterruptedException {
        ingestion.publish(sampleIds);
        snapshots.waitUntilServed(sampleIds);
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
        searchCases(terms.termIn(bookNodes, "de", "faust"), terms.termIn(bookNodes, "fr", "monde"))
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

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
