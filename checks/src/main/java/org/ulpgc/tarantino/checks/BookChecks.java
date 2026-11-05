package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.model.ProblemCode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.ulpgc.tarantino.checks.ApiNames.AUTHOR;
import static org.ulpgc.tarantino.checks.ApiNames.BOOKS_PATH;
import static org.ulpgc.tarantino.checks.ApiNames.BOOK_ID;
import static org.ulpgc.tarantino.checks.ApiNames.LANGUAGE;
import static org.ulpgc.tarantino.checks.ApiNames.LANGUAGES;
import static org.ulpgc.tarantino.checks.ApiNames.TITLE;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertEquals;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertStatus;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertTrue;
import static org.ulpgc.tarantino.checks.SampleBooks.BEYOND_INT32;
import static org.ulpgc.tarantino.checks.SampleBooks.MISSING_BOOK;
import static org.ulpgc.tarantino.checks.SampleBooks.PRIDE_AND_PREJUDICE;

/** {@code GET /books/{book_id}} on the front-end port (SPEC §6.4): every sample book, the fields of one and the errors. */
final class BookChecks {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookChecks.class);
    private static final String TITLE_OF_1342 = "Pride and Prejudice";
    private static final String AUTHOR_OF_1342 = "Jane Austen";
    private static final String ENGLISH = "en";
    private static final List<String> INVALID_BOOK_IDS = List.of("0", "-5", "01", String.valueOf(BEYOND_INT32), "abc");

    private final CheckedClient client;
    private final ProblemChecks problems;
    private final String frontEndUrl;

    BookChecks(CheckedClient client, ProblemChecks problems, String frontEndUrl) {
        this.client = client;
        this.problems = problems;
        this.frontEndUrl = frontEndUrl;
    }

    /** The {@code /books} answer of each sample book, which the language filter checks then search. */
    List<JsonNode> check(List<Integer> sampleIds) throws IOException, InterruptedException {
        LOGGER.info("Checking /books endpoints");
        List<JsonNode> sampleBooks = new ArrayList<>();
        for (int bookId : sampleIds) {
            sampleBooks.add(book(bookId));
        }
        checkPrideAndPrejudice();
        checkErrors();
        return sampleBooks;
    }

    private JsonNode book(int bookId) throws IOException, InterruptedException {
        String path = BOOKS_PATH + bookId;
        CheckedClient.Response response = client.get(frontEndUrl + path);
        assertStatus(HTTP_OK, response, "GET " + path);
        assertEquals(bookId, response.json().get(BOOK_ID).asInt(), "the book_id of GET " + path);
        return response.json();
    }

    private void checkPrideAndPrejudice() throws IOException, InterruptedException {
        JsonNode book = book(PRIDE_AND_PREJUDICE);
        String of = " of GET " + BOOKS_PATH + PRIDE_AND_PREJUDICE;
        assertEquals(TITLE_OF_1342, book.get(TITLE).asText(), "the title" + of);
        assertEquals(AUTHOR_OF_1342, book.get(AUTHOR).asText(), "the author" + of);
        assertEquals(ENGLISH, book.get(LANGUAGE).asText(), "the language" + of);
        assertTrue(book.has(LANGUAGES) && book.get(LANGUAGES).isArray(), "GET " + BOOKS_PATH + PRIDE_AND_PREJUDICE + " has a languages array");
        assertEquals(ENGLISH, book.get(LANGUAGES).get(0).asText(), "the first language" + of);
    }

    private void checkErrors() throws IOException, InterruptedException {
        problems.get(frontEndUrl + BOOKS_PATH + MISSING_BOOK, ProblemCode.BOOK_NOT_FOUND);
        for (String invalid : INVALID_BOOK_IDS) {
            problems.get(frontEndUrl + BOOKS_PATH + invalid, ProblemCode.INVALID_PARAMETER, BOOK_ID);
        }
    }
}
