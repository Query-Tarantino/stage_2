package org.ulpgc.tarantino.checks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.model.BookStatus;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.net.HttpURLConnection.HTTP_ACCEPTED;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.ulpgc.tarantino.checks.ApiNames.INGEST;
import static org.ulpgc.tarantino.checks.ApiNames.INGEST_STATUS;
import static org.ulpgc.tarantino.checks.ApiNames.LAST_ERROR;
import static org.ulpgc.tarantino.checks.ApiNames.LOCATION;
import static org.ulpgc.tarantino.checks.ApiNames.STATUS;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertTrue;

/** Brings the sample books to {@code PUBLISHED} with {@code POST /ingest/{book_id}} and follows each one (SPEC §6.2 and §6.7). */
final class SampleIngestion {

    private static final Logger LOGGER = LoggerFactory.getLogger(SampleIngestion.class);
    private static final Duration MAX_WAIT = Duration.ofSeconds(60);
    private static final Duration STATUS_POLL = Duration.ofSeconds(1);
    private static final String PUBLISHED = BookStatus.PUBLISHED.name();
    private static final String FAILED = BookStatus.FAILED.name();
    private static final String UNKNOWN_ERROR = "unknown";

    private record BookStatusCheck(int bookId, String statusUrl) {
    }

    private final CheckedClient client;
    private final String operationalUrl;

    SampleIngestion(CheckedClient client, String operationalUrl) {
        this.client = client;
        this.operationalUrl = operationalUrl;
    }

    void publish(List<Integer> sampleIds) throws IOException, InterruptedException {
        LOGGER.info("Ensuring all sample books are published");
        List<BookStatusCheck> pending = new ArrayList<>();
        for (int bookId : sampleIds) {
            ingest(bookId).ifPresent(pending::add);
        }
        for (BookStatusCheck check : pending) {
            waitForPublished(check.bookId(), check.statusUrl());
        }
    }

    private Optional<BookStatusCheck> ingest(int bookId) throws IOException, InterruptedException {
        String request = "POST " + INGEST + bookId;
        CheckedClient.Response response = client.post(operationalUrl + INGEST + bookId, "");
        assertTrue(response.status() == HTTP_OK || response.status() == HTTP_ACCEPTED, request + " answers 200 or 202");
        if (response.status() == HTTP_ACCEPTED) {
            String location = response.header(LOCATION);
            assertTrue(location != null && !location.isBlank(), "the 202 of " + request + " has a Location");
            return Optional.of(new BookStatusCheck(bookId, operationalUrl + (location.startsWith("/") ? location : "/" + location)));
        }
        boolean published = PUBLISHED.equals(response.json().get(STATUS).asText());
        return published ? Optional.empty() : Optional.of(new BookStatusCheck(bookId, operationalUrl + INGEST_STATUS + bookId));
    }

    private void waitForPublished(int bookId, String statusUrl) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + MAX_WAIT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            CheckedClient.Response response = client.get(statusUrl);
            String status = statusOf(response);
            if (PUBLISHED.equals(status)) {
                return;
            }
            if (FAILED.equals(status)) {
                throw new AssertionError("Book " + bookId + " failed ingestion: " + response.json().path(LAST_ERROR).asText(UNKNOWN_ERROR));
            }
            Thread.sleep(STATUS_POLL);
        }
        throw new AssertionError("Timed out waiting for book " + bookId + " to reach PUBLISHED at " + statusUrl);
    }

    private static String statusOf(CheckedClient.Response response) {
        boolean answered = response.status() == HTTP_OK && response.json() != null && response.json().has(STATUS);
        return answered ? response.json().get(STATUS).asText() : "";
    }
}
