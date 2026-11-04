package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.model.ProblemCode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.ulpgc.tarantino.checks.Assertions.assertEquals;
import static org.ulpgc.tarantino.checks.Assertions.assertProblem;
import static org.ulpgc.tarantino.checks.Assertions.assertTrue;
import static org.ulpgc.tarantino.checks.SampleBooks.OUTSIDE_THE_CORPUS;
import static org.ulpgc.tarantino.checks.SampleBooks.PRIDE_AND_PREJUDICE;

/** {@code GET /books/{book_id}} (SPEC §6.4): every sample book, the metadata of one of them and the invalid ids. */
final class BookChecks {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookChecks.class);
    private static final List<String> INVALID_BOOK_IDS = List.of("0", "-5", "01", "2147483648", "abc");
    private static final String BOOKS = "/books/";
    private static final String BOOK_ID = "book_id";
    private static final String LANGUAGES = "languages";
    private static final String ENGLISH = "en";
    private static final int OK = 200;

    private final Api api;

    BookChecks(Api api) {
        this.api = api;
    }

    /** The answers of the sample books, for the language checks of {@link SearchChecks}. */
    List<JsonNode> check(List<Integer> sampleIds) throws IOException, InterruptedException {
        LOGGER.info("Checking /books endpoints");
        List<JsonNode> sampleBooks = new ArrayList<>();
        for (int bookId : sampleIds) {
            sampleBooks.add(sampleBook(bookId));
        }
        checkPrideAndPrejudice();
        assertProblem(api.frontEnd(BOOKS + OUTSIDE_THE_CORPUS), ProblemCode.BOOK_NOT_FOUND);
        for (String invalidBookId : INVALID_BOOK_IDS) {
            assertProblem(api.frontEnd(BOOKS + invalidBookId), ProblemCode.INVALID_PARAMETER, BOOK_ID);
        }
        return sampleBooks;
    }

    private JsonNode sampleBook(int bookId) throws IOException, InterruptedException {
        CheckedClient.Response response = api.frontEnd(BOOKS + bookId);
        assertEquals(OK, response.status());
        assertEquals(bookId, response.json().get(BOOK_ID).asInt());
        return response.json();
    }

    private void checkPrideAndPrejudice() throws IOException, InterruptedException {
        JsonNode book = sampleBook(PRIDE_AND_PREJUDICE);
        assertEquals("Pride and Prejudice", book.get("title").asText());
        assertEquals("Jane Austen", book.get("author").asText());
        assertEquals(ENGLISH, book.get("language").asText());
        assertTrue(book.has(LANGUAGES) && book.get(LANGUAGES).isArray());
        assertEquals(ENGLISH, book.get(LANGUAGES).get(0).asText());
    }
}
