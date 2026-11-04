package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.ProblemCode;

import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static org.ulpgc.tarantino.checks.Assertions.assertEquals;
import static org.ulpgc.tarantino.checks.Assertions.assertProblem;
import static org.ulpgc.tarantino.checks.Assertions.assertTrue;
import static org.ulpgc.tarantino.checks.SampleBooks.OUTSIDE_THE_CORPUS;
import static org.ulpgc.tarantino.checks.SampleBooks.PRIDE_AND_PREJUDICE;

/**
 * The operational endpoints of nginx port 8081 (SPEC §6.1, §6.2 and §6.5): their summaries, the state of one book, the
 * errors of an invalid or unknown {@code book_id}, and that a repeated request changes nothing (SPEC §6.7).
 */
final class OperationalChecks {

    private static final Logger LOGGER = LoggerFactory.getLogger(OperationalChecks.class);
    private static final String NOT_POSITIVE = "0";
    private static final String NOT_A_NUMBER = "abc";
    private static final List<String> INVALID_BOOK_IDS = List.of(NOT_POSITIVE, NOT_A_NUMBER);
    private static final String BOOK_ID = "book_id";
    private static final String STATUS = "status";
    private static final String LOCATION = "Location";
    private static final String INGEST = "/ingest/";
    private static final String INGEST_STATUS = "/ingest/status/";
    private static final String INDEX_STATUS = "/index/status/";
    private static final String INDEX_UPDATE = "/index/update/";
    private static final String INGEST_LIST = "/ingest/list";
    private static final int OK = 200;

    private final Api api;

    OperationalChecks(Api api) {
        this.api = api;
    }

    void checkSummaries() throws IOException, InterruptedException {
        LOGGER.info("Checking operational status endpoints");
        assertFields(api.operational(INGEST_LIST), "books");
        assertFields(api.operational("/index/status"), "generation", "books_indexed");
        assertFields(api.operational("/control/status"), "books", "queues");
    }

    void checkBookStatuses() throws IOException, InterruptedException {
        LOGGER.info("Checking status endpoints and operational errors");
        assertStateOf(api.operational(INGEST_STATUS + PRIDE_AND_PREJUDICE));
        assertStateOf(api.operational(INDEX_STATUS + PRIDE_AND_PREJUDICE));
        assertProblem(api.operational(INGEST_STATUS + OUTSIDE_THE_CORPUS), ProblemCode.BOOK_NOT_FOUND);
        assertProblem(api.operational(INDEX_STATUS + OUTSIDE_THE_CORPUS), ProblemCode.BOOK_NOT_FOUND);
        for (String statusPath : List.of(INGEST_STATUS, INDEX_STATUS)) {
            for (String invalidBookId : INVALID_BOOK_IDS) {
                assertProblem(api.operational(statusPath + invalidBookId), ProblemCode.INVALID_PARAMETER, BOOK_ID);
            }
        }
        assertProblem(api.postOperational(INGEST + NOT_POSITIVE), ProblemCode.INVALID_PARAMETER, BOOK_ID);
        assertProblem(api.postOperational(INDEX_UPDATE + NOT_POSITIVE), ProblemCode.INVALID_PARAMETER, BOOK_ID);
        assertProblem(api.postOperational(INDEX_UPDATE + OUTSIDE_THE_CORPUS), ProblemCode.BOOK_NOT_FOUND);
    }

    /** Asking again for a published book answers {@code 200} with its state and no {@code Location}, and lists it once. */
    void checkIdempotency(List<Integer> sampleIds) throws IOException, InterruptedException {
        LOGGER.info("Verifying idempotency for published sample books");
        for (int bookId : sampleIds) {
            assertAlreadyPublished(api.postOperational(INGEST + bookId));
            assertAlreadyPublished(api.postOperational(INDEX_UPDATE + bookId));
        }
        checkIngestList(sampleIds);
    }

    private void checkIngestList(List<Integer> sampleIds) throws IOException, InterruptedException {
        CheckedClient.Response list = api.operational(INGEST_LIST);
        assertEquals(OK, list.status());
        JsonNode books = list.json().get("books");
        assertTrue(books != null && books.isArray());
        List<Integer> ingestedBooks = StreamSupport.stream(books.spliterator(), false).map(JsonNode::asInt).toList();
        assertEquals(ingestedBooks.size(), list.json().get("count").asInt());
        assertTrue(ingestedBooks.containsAll(sampleIds));
        IntStream.range(1, ingestedBooks.size()).forEach(index -> assertTrue(ingestedBooks.get(index) > ingestedBooks.get(index - 1)));
    }

    private static void assertFields(CheckedClient.Response response, String... fields) {
        assertEquals(OK, response.status());
        List.of(fields).forEach(field -> assertTrue(response.json().has(field)));
    }

    private static void assertStateOf(CheckedClient.Response response) {
        assertEquals(OK, response.status());
        assertEquals(PRIDE_AND_PREJUDICE, response.json().get(BOOK_ID).asInt());
    }

    private static void assertAlreadyPublished(CheckedClient.Response response) {
        assertEquals(OK, response.status());
        assertTrue(response.header(LOCATION) == null);
        assertEquals(BookStatus.PUBLISHED.name(), response.json().get(STATUS).asText());
    }
}
