package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.ProblemCode;

import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.ulpgc.tarantino.checks.ApiNames.BOOKS;
import static org.ulpgc.tarantino.checks.ApiNames.BOOKS_INDEXED;
import static org.ulpgc.tarantino.checks.ApiNames.BOOK_ID;
import static org.ulpgc.tarantino.checks.ApiNames.CONTROL_STATUS;
import static org.ulpgc.tarantino.checks.ApiNames.COUNT;
import static org.ulpgc.tarantino.checks.ApiNames.GENERATION;
import static org.ulpgc.tarantino.checks.ApiNames.INDEX_STATUS;
import static org.ulpgc.tarantino.checks.ApiNames.INDEX_UPDATE;
import static org.ulpgc.tarantino.checks.ApiNames.INGEST;
import static org.ulpgc.tarantino.checks.ApiNames.INGEST_LIST;
import static org.ulpgc.tarantino.checks.ApiNames.INGEST_STATUS;
import static org.ulpgc.tarantino.checks.ApiNames.LOCATION;
import static org.ulpgc.tarantino.checks.ApiNames.QUEUES;
import static org.ulpgc.tarantino.checks.ApiNames.STATUS;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertEquals;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertStatus;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertTrue;
import static org.ulpgc.tarantino.checks.SampleBooks.MISSING_BOOK;
import static org.ulpgc.tarantino.checks.SampleBooks.PRIDE_AND_PREJUDICE;

/** The operational endpoints of SPEC §6.5 on the operational entry port, their errors and the idempotency of SPEC §6.7. */
final class OperationalChecks {

    private static final Logger LOGGER = LoggerFactory.getLogger(OperationalChecks.class);
    private static final String PUBLISHED = BookStatus.PUBLISHED.name();
    private static final List<String> INVALID_BOOK_IDS = List.of("0", "abc");
    private static final String NOT_A_BOOK = "0";

    private final CheckedClient client;
    private final ProblemChecks problems;
    private final String operationalUrl;

    OperationalChecks(CheckedClient client, ProblemChecks problems, String operationalUrl) {
        this.client = client;
        this.problems = problems;
        this.operationalUrl = operationalUrl;
    }

    void checkStatusPages() throws IOException, InterruptedException {
        LOGGER.info("Checking operational status endpoints");
        assertFields(INGEST_LIST, BOOKS);
        assertFields(INDEX_STATUS, GENERATION, BOOKS_INDEXED);
        assertFields(CONTROL_STATUS, BOOKS, QUEUES);
    }

    void checkBookStatus() throws IOException, InterruptedException {
        LOGGER.info("Checking status endpoints and operational errors");
        for (String statusPath : List.of(INGEST_STATUS, INDEX_STATUS + "/")) {
            assertBookStatus(statusPath + PRIDE_AND_PREJUDICE);
            problems.get(operationalUrl + statusPath + MISSING_BOOK, ProblemCode.BOOK_NOT_FOUND);
            for (String invalid : INVALID_BOOK_IDS) {
                problems.get(operationalUrl + statusPath + invalid, ProblemCode.INVALID_PARAMETER, BOOK_ID);
            }
        }
        problems.post(operationalUrl + INGEST + NOT_A_BOOK, ProblemCode.INVALID_PARAMETER, BOOK_ID);
        problems.post(operationalUrl + INDEX_UPDATE + NOT_A_BOOK, ProblemCode.INVALID_PARAMETER, BOOK_ID);
        problems.post(operationalUrl + INDEX_UPDATE + MISSING_BOOK, ProblemCode.BOOK_NOT_FOUND);
    }

    void checkIdempotency(List<Integer> sampleIds) throws IOException, InterruptedException {
        LOGGER.info("Verifying idempotency for published sample books");
        for (int bookId : sampleIds) {
            assertAlreadyPublished(INGEST + bookId);
            assertAlreadyPublished(INDEX_UPDATE + bookId);
        }
        checkIngestList(sampleIds);
    }

    private void assertFields(String path, String... fields) throws IOException, InterruptedException {
        CheckedClient.Response response = client.get(operationalUrl + path);
        assertStatus(HTTP_OK, response, "GET " + path);
        Stream.of(fields).forEach(field -> assertTrue(response.json().has(field), "GET " + path + " has " + field));
    }

    private void assertBookStatus(String path) throws IOException, InterruptedException {
        CheckedClient.Response response = client.get(operationalUrl + path);
        assertStatus(HTTP_OK, response, "GET " + path);
        assertEquals(PRIDE_AND_PREJUDICE, response.json().get(BOOK_ID).asInt(), "the book_id of GET " + path);
    }

    private void assertAlreadyPublished(String path) throws IOException, InterruptedException {
        CheckedClient.Response response = client.post(operationalUrl + path, "");
        assertStatus(HTTP_OK, response, "POST " + path);
        assertTrue(response.header(LOCATION) == null, "POST " + path + " of a published book has no Location");
        assertEquals(PUBLISHED, response.json().get(STATUS).asText(), "the status of the book of POST " + path);
    }

    private void checkIngestList(List<Integer> sampleIds) throws IOException, InterruptedException {
        CheckedClient.Response response = client.get(operationalUrl + INGEST_LIST);
        assertStatus(HTTP_OK, response, "GET " + INGEST_LIST);
        JsonNode books = response.json().get(BOOKS);
        assertTrue(books != null && books.isArray(), "GET /ingest/list has a books array");
        List<Integer> ingested = StreamSupport.stream(books.spliterator(), false).map(JsonNode::asInt).toList();
        assertEquals(ingested.size(), response.json().get(COUNT).asInt(), "the count of GET /ingest/list");
        assertTrue(ingested.containsAll(sampleIds), "GET /ingest/list holds every sample book");
        IntStream.range(1, ingested.size()).forEach(index -> assertTrue(ingested.get(index) > ingested.get(index - 1),
                "GET /ingest/list is in ascending book_id order at position " + index));
    }
}
