package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.ulpgc.tarantino.checks.ApiNames.AUTHOR;
import static org.ulpgc.tarantino.checks.ApiNames.BOOKS_PATH;
import static org.ulpgc.tarantino.checks.ApiNames.LANGUAGE;
import static org.ulpgc.tarantino.checks.ApiNames.LANGUAGES;
import static org.ulpgc.tarantino.checks.ApiNames.ORIGINAL_YEAR;
import static org.ulpgc.tarantino.checks.ApiNames.RESULTS;
import static org.ulpgc.tarantino.checks.ApiNames.SEARCH;
import static org.ulpgc.tarantino.checks.ApiNames.TITLE;
import static org.ulpgc.tarantino.checks.ApiNames.TOTAL;
import static org.ulpgc.tarantino.checks.ApiNames.YEAR;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertEquals;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertTrue;

/** Compares the answers of the stack with {@code checks/expected.json}, generated once over the sample books (SPEC §9). */
final class ExpectedAnswers {

    static final String SEARCHES = "searches";
    static final String BOOKS = "books";
    static final String PATH = "path";
    static final String BOOK_IDS = "book_ids";

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpectedAnswers.class);
    private static final ObjectMapper MAPPER = TarantinoJson.mapper();
    private static final List<String> TEXT_FIELDS = List.of(TITLE, AUTHOR, LANGUAGE);

    private final CheckedClient client;
    private final String frontEndUrl;

    ExpectedAnswers(CheckedClient client, String frontEndUrl) {
        this.client = client;
        this.frontEndUrl = frontEndUrl;
    }

    void check(Path expectedPath) throws IOException, InterruptedException {
        LOGGER.info("Validating responses against expected.json: {}", expectedPath);
        JsonNode expected = MAPPER.readTree(expectedPath.toFile());
        for (Map.Entry<String, JsonNode> search : entries(expected, SEARCHES)) {
            checkSearch(search.getKey(), search.getValue());
        }
        for (Map.Entry<String, JsonNode> book : entries(expected, BOOKS)) {
            checkBook(book.getKey(), book.getValue());
        }
    }

    private static List<Map.Entry<String, JsonNode>> entries(JsonNode expected, String field) {
        return expected.has(field) ? List.copyOf(expected.get(field).properties()) : List.of();
    }

    private void checkSearch(String name, JsonNode expected) throws IOException, InterruptedException {
        String path = expected.has(PATH) ? expected.get(PATH).asText() : SEARCH + "?q=" + name.replace(" ", "%20");
        String search = "expected search " + name + " (" + path + ")";
        CheckedClient.Response response = client.get(frontEndUrl + path);
        assertEquals(HTTP_OK, response.status(), "the status of " + search);
        if (expected.has(TOTAL)) {
            assertEquals(expected.get(TOTAL).asInt(), response.json().get(TOTAL).asInt(), "the total of " + search);
        }
        if (expected.has(BOOK_IDS) && expected.get(BOOK_IDS).isArray()) {
            List<Integer> expectedIds = StreamSupport.stream(expected.get(BOOK_IDS).spliterator(), false).map(JsonNode::asInt).toList();
            assertEquals(expectedIds, SearchResults.bookIds(response.json().get(RESULTS)), "the book_ids of " + search);
        }
    }

    private void checkBook(String bookId, JsonNode expected) throws IOException, InterruptedException {
        String path = BOOKS_PATH + bookId;
        CheckedClient.Response response = client.get(frontEndUrl + path);
        assertEquals(HTTP_OK, response.status(), "the status of expected book " + path);
        JsonNode actual = response.json();
        TEXT_FIELDS.stream().filter(expected::hasNonNull).forEach(field ->
                assertEquals(expected.get(field).asText(), actual.get(field).asText(), "the " + field + " of " + path));
        if (expected.hasNonNull(YEAR)) {
            assertEquals(expected.get(YEAR).asInt(), actual.get(YEAR).asInt(), "the year of " + path);
        }
        if (expected.hasNonNull(LANGUAGES)) {
            assertEquals(expected.get(LANGUAGES), actual.get(LANGUAGES), "the languages of " + path);
        }
        if (expected.has(ORIGINAL_YEAR)) {
            assertSameOriginalYear(path, expected.get(ORIGINAL_YEAR), actual.get(ORIGINAL_YEAR));
        }
    }

    private static void assertSameOriginalYear(String path, JsonNode expected, JsonNode actual) {
        if (expected.isNull()) {
            assertTrue(actual == null || actual.isNull(), path + " has a null original_year");
            return;
        }
        assertTrue(actual != null && !actual.isNull(), path + " has an original_year");
        assertEquals(expected.asInt(), actual.asInt(), "the original_year of " + path);
    }
}
