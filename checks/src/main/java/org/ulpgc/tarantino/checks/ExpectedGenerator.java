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
import java.util.ArrayList;
import java.util.List;

/**
 * Writes {@code checks/expected.json} from the running stack (SPEC §9): the metadata of each sample book and the answer
 * of each search case, for a person to review before {@link ExpectedChecks} relies on it.
 */
final class ExpectedGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpectedGenerator.class);
    private static final ObjectMapper MAPPER = TarantinoJson.mapper();
    private static final List<String> BOOK_FIELDS = List.of("title", "author", "language", "year");
    private static final String LANGUAGES = "languages";
    private static final String ORIGINAL_YEAR = "original_year";
    private static final String GERMAN_FALLBACK = "faust";
    private static final String FRENCH_FALLBACK = "monde";
    private static final int OK = 200;

    private record SearchCase(String name, String path) {
    }

    private final Api api;
    private final SearchTerms terms;
    private final PublicationWait publication;

    ExpectedGenerator(Api api, SearchTerms terms, PublicationWait publication) {
        this.api = api;
        this.terms = terms;
        this.publication = publication;
    }

    void generate(List<Integer> sampleIds, Path expectedPath) throws IOException, InterruptedException {
        LOGGER.info("Generating expected answers to {}", expectedPath);
        publication.ensurePublished(sampleIds);
        ObjectNode root = MAPPER.createObjectNode();
        root.put("total_books", sampleIds.size());
        ArrayNode ids = root.putArray("sample_ids");
        sampleIds.forEach(ids::add);
        ObjectNode searches = root.putObject("searches");
        ObjectNode books = root.putObject("books");
        writeSearches(searchCases(writeBooks(sampleIds, books)), searches);
        if (expectedPath.getParent() != null) {
            Files.createDirectories(expectedPath.getParent());
        }
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(expectedPath.toFile(), root);
        LOGGER.info("Generated expected.json with {} books and {} queries", books.size(), searches.size());
    }

    /** Each sample book that answers, written to {@code books}; their answers, for the language cases. */
    private List<JsonNode> writeBooks(List<Integer> sampleIds, ObjectNode books) throws IOException, InterruptedException {
        List<JsonNode> sampleBooks = new ArrayList<>();
        for (int bookId : sampleIds) {
            CheckedClient.Response response = api.frontEnd("/books/" + bookId);
            if (response.status() == OK && response.json() != null) {
                writeBook(books.putObject(String.valueOf(bookId)), bookId, response.json());
                sampleBooks.add(response.json());
            }
        }
        return sampleBooks;
    }

    private static void writeBook(ObjectNode book, int bookId, JsonNode answer) {
        book.put("book_id", bookId);
        BOOK_FIELDS.forEach(field -> book.set(field, answer.get(field)));
        if (answer.has(LANGUAGES)) {
            book.set(LANGUAGES, answer.get(LANGUAGES));
        }
        book.set(ORIGINAL_YEAR, answer.get(ORIGINAL_YEAR));
    }

    private List<SearchCase> searchCases(List<JsonNode> sampleBooks) {
        String germanTerm = SearchTerms.firstIn(sampleBooks, "de").map(terms::termFor).orElse(GERMAN_FALLBACK);
        String frenchTerm = SearchTerms.firstIn(sampleBooks, "fr").map(terms::termFor).orElse(FRENCH_FALLBACK);
        return List.of(
                new SearchCase("single_word", "/search?q=pride"),
                new SearchCase("multi_word", "/search?q=pride%20prejudice"),
                new SearchCase("filter_author", "/search?q=pride&author=Austen"),
                new SearchCase("filter_language", "/search?q=pride&language=en"),
                new SearchCase("filter_language_de", "/search?q=" + germanTerm + "&language=de"),
                new SearchCase("filter_language_fr", "/search?q=" + frenchTerm + "&language=fr"),
                new SearchCase("filter_language_zz", "/search?q=pride&language=zz"),
                new SearchCase("filter_year", "/search?q=pride&year=1998"),
                new SearchCase("combined_filter", "/search?q=pride&author=Austen&language=en&year=1998"),
                new SearchCase("pagination_page0", "/search?q=pride&offset=0&limit=1"),
                new SearchCase("pagination_page1", "/search?q=pride&offset=1&limit=1"),
                new SearchCase("pagination_large_offset", "/search?q=pride&offset=1000000000"),
                new SearchCase("stopwords_only", "/search?q=the%20and"),
                new SearchCase("empty_results", "/search?q=pyromaniac%20liliput"));
    }

    private void writeSearches(List<SearchCase> searchCases, ObjectNode searches) throws IOException, InterruptedException {
        for (SearchCase searchCase : searchCases) {
            JsonNode answer = api.frontEnd(searchCase.path()).json();
            ObjectNode search = searches.putObject(searchCase.name());
            search.put("path", searchCase.path());
            search.put("total", answer.get("total").asInt());
            ArrayNode bookIds = search.putArray("book_ids");
            SearchResults.bookIds(answer.get("results")).forEach(bookIds::add);
        }
    }
}
