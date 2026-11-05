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

import static java.net.HttpURLConnection.HTTP_OK;
import static org.ulpgc.tarantino.checks.ApiNames.AUTHOR;
import static org.ulpgc.tarantino.checks.ApiNames.BOOKS_PATH;
import static org.ulpgc.tarantino.checks.ApiNames.BOOK_ID;
import static org.ulpgc.tarantino.checks.ApiNames.LANGUAGE;
import static org.ulpgc.tarantino.checks.ApiNames.LANGUAGES;
import static org.ulpgc.tarantino.checks.ApiNames.ORIGINAL_YEAR;
import static org.ulpgc.tarantino.checks.ApiNames.RESULTS;
import static org.ulpgc.tarantino.checks.ApiNames.TITLE;
import static org.ulpgc.tarantino.checks.ApiNames.TOTAL;
import static org.ulpgc.tarantino.checks.ApiNames.TOTAL_BOOKS;
import static org.ulpgc.tarantino.checks.ApiNames.YEAR;
import static org.ulpgc.tarantino.checks.ExpectedAnswers.BOOKS;
import static org.ulpgc.tarantino.checks.ExpectedAnswers.BOOK_IDS;
import static org.ulpgc.tarantino.checks.ExpectedAnswers.PATH;
import static org.ulpgc.tarantino.checks.ExpectedAnswers.SEARCHES;

/** Writes {@code checks/expected.json} from the answers of the running stack: each sample book and each search case (SPEC §9). */
final class ExpectedAnswersWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpectedAnswersWriter.class);
    private static final ObjectMapper MAPPER = TarantinoJson.mapper();
    private static final String SAMPLE_IDS = "sample_ids";
    private static final List<String> BOOK_FIELDS = List.of(TITLE, AUTHOR, LANGUAGE, YEAR);
    private static final String GERMAN = "de";
    private static final String GERMAN_FALLBACK = "faust";
    private static final String FRENCH = "fr";
    private static final String FRENCH_FALLBACK = "monde";

    private final CheckedClient client;
    private final SearchTerms terms;
    private final String frontEndUrl;

    ExpectedAnswersWriter(CheckedClient client, SearchTerms terms, String frontEndUrl) {
        this.client = client;
        this.terms = terms;
        this.frontEndUrl = frontEndUrl;
    }

    void write(List<Integer> sampleIds, Path expectedPath) throws IOException, InterruptedException {
        LOGGER.info("Generating expected answers to {}", expectedPath);
        ObjectNode root = MAPPER.createObjectNode();
        root.put(TOTAL_BOOKS, sampleIds.size());
        ArrayNode ids = root.putArray(SAMPLE_IDS);
        sampleIds.forEach(ids::add);
        ObjectNode searches = root.putObject(SEARCHES);
        ObjectNode books = root.putObject(BOOKS);
        List<JsonNode> sampleBooks = expectedBooks(sampleIds, books);
        String germanTerm = terms.termIn(sampleBooks, GERMAN, GERMAN_FALLBACK);
        String frenchTerm = terms.termIn(sampleBooks, FRENCH, FRENCH_FALLBACK);
        for (SearchCase searchCase : SearchCase.all(germanTerm, frenchTerm)) {
            expectedSearch(searches, searchCase);
        }
        if (expectedPath.getParent() != null) {
            Files.createDirectories(expectedPath.getParent());
        }
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(expectedPath.toFile(), root);
        LOGGER.info("Generated expected.json with {} books and {} queries", books.size(), searches.size());
    }

    private List<JsonNode> expectedBooks(List<Integer> bookIds, ObjectNode books) throws IOException, InterruptedException {
        List<JsonNode> found = new ArrayList<>();
        for (int bookId : bookIds) {
            CheckedClient.Response response = client.get(frontEndUrl + BOOKS_PATH + bookId);
            if (response.status() == HTTP_OK && response.json() != null) {
                expectedBook(books.putObject(String.valueOf(bookId)), bookId, response.json());
                found.add(response.json());
            }
        }
        return found;
    }

    private static void expectedBook(ObjectNode expected, int bookId, JsonNode book) {
        expected.put(BOOK_ID, bookId);
        BOOK_FIELDS.forEach(field -> expected.set(field, book.get(field)));
        if (book.has(LANGUAGES)) {
            expected.set(LANGUAGES, book.get(LANGUAGES));
        }
        expected.set(ORIGINAL_YEAR, book.get(ORIGINAL_YEAR));
    }

    private void expectedSearch(ObjectNode searches, SearchCase searchCase) throws IOException, InterruptedException {
        CheckedClient.Response response = client.get(frontEndUrl + searchCase.path());
        ObjectNode expected = searches.putObject(searchCase.name());
        expected.put(PATH, searchCase.path());
        expected.put(TOTAL, response.json().get(TOTAL).asInt());
        ArrayNode bookIds = expected.putArray(BOOK_IDS);
        SearchResults.bookIds(response.json().get(RESULTS)).forEach(bookIds::add);
    }
}
