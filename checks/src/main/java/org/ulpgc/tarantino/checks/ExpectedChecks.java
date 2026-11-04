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

import static org.ulpgc.tarantino.checks.Assertions.assertEquals;
import static org.ulpgc.tarantino.checks.Assertions.assertTrue;

/** The answers over the sample books in {@code checks/expected.json}, generated once and reviewed by hand (SPEC §9). */
final class ExpectedChecks {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpectedChecks.class);
    private static final ObjectMapper MAPPER = TarantinoJson.mapper();
    private static final List<String> TEXT_FIELDS = List.of("title", "author", "language");
    private static final String SEARCHES = "searches";
    private static final String BOOKS = "books";
    private static final String PATH = "path";
    private static final String TOTAL = "total";
    private static final String BOOK_IDS = "book_ids";
    private static final String YEAR = "year";
    private static final String LANGUAGES = "languages";
    private static final String ORIGINAL_YEAR = "original_year";
    private static final String SPACE = " ";
    private static final String ENCODED_SPACE = "%20";
    private static final int OK = 200;

    private final Api api;

    ExpectedChecks(Api api) {
        this.api = api;
    }

    void check(Path expectedPath) throws IOException, InterruptedException {
        LOGGER.info("Validating responses against expected.json: {}", expectedPath);
        JsonNode expected = MAPPER.readTree(expectedPath.toFile());
        for (Map.Entry<String, JsonNode> search : expected.path(SEARCHES).properties()) {
            checkSearch(search.getKey(), search.getValue());
        }
        for (Map.Entry<String, JsonNode> book : expected.path(BOOKS).properties()) {
            checkBook(book.getKey(), book.getValue());
        }
    }

    private void checkSearch(String name, JsonNode expected) throws IOException, InterruptedException {
        String path = expected.has(PATH) ? expected.get(PATH).asText() : "/search?q=" + name.replace(SPACE, ENCODED_SPACE);
        JsonNode answer = ok(path);
        if (expected.has(TOTAL)) {
            assertEquals(expected.get(TOTAL).asInt(), answer.get(TOTAL).asInt());
        }
        if (expected.path(BOOK_IDS).isArray()) {
            List<Integer> expectedBookIds = StreamSupport.stream(expected.get(BOOK_IDS).spliterator(), false).map(JsonNode::asInt).toList();
            assertEquals(expectedBookIds, SearchResults.bookIds(answer.get("results")));
        }
    }

    private void checkBook(String bookId, JsonNode expected) throws IOException, InterruptedException {
        JsonNode answer = ok("/books/" + bookId);
        TEXT_FIELDS.stream()
                .filter(field -> present(expected, field))
                .forEach(field -> assertEquals(expected.get(field).asText(), answer.get(field).asText()));
        if (present(expected, YEAR)) {
            assertEquals(expected.get(YEAR).asInt(), answer.get(YEAR).asInt());
        }
        if (present(expected, LANGUAGES)) {
            assertEquals(expected.get(LANGUAGES), answer.get(LANGUAGES));
        }
        if (expected.has(ORIGINAL_YEAR)) {
            checkOriginalYear(expected.get(ORIGINAL_YEAR), answer.get(ORIGINAL_YEAR));
        }
    }

    private static void checkOriginalYear(JsonNode expected, JsonNode actual) {
        if (expected.isNull()) {
            assertTrue(actual == null || actual.isNull());
            return;
        }
        assertTrue(actual != null && !actual.isNull());
        assertEquals(expected.asInt(), actual.asInt());
    }

    private JsonNode ok(String path) throws IOException, InterruptedException {
        CheckedClient.Response response = api.frontEnd(path);
        assertEquals(OK, response.status());
        return response.json();
    }

    private static boolean present(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull();
    }
}
