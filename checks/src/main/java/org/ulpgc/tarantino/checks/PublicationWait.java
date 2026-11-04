package org.ulpgc.tarantino.checks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.model.BookStatus;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.ulpgc.tarantino.checks.Assertions.assertTrue;
import static org.ulpgc.tarantino.checks.SampleBooks.PRIDE_AND_PREJUDICE;
import static org.ulpgc.tarantino.checks.SearchResults.containsBook;

/**
 * Brings the sample books to {@code PUBLISHED} through {@code POST /ingest} (SPEC §6.2) and waits until every
 * {@code query} replica shows them (SPEC §7): in {@code /search/filters}, in {@code /books/{book_id}} and in
 * {@code /search}.
 */
final class PublicationWait {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicationWait.class);
    private static final Duration MAX_WAIT = Duration.ofSeconds(60);
    private static final Duration STATUS_POLL = Duration.ofSeconds(1);
    private static final Duration ANSWER_POLL = Duration.ofMillis(200);
    private static final Duration BOOK_POLL = Duration.ofMillis(500);
    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(2);
    private static final String SNAPSHOT_SECONDS = "TARANTINO_SNAPSHOT_SECONDS";
    private static final String DEFAULT_SNAPSHOT_SECONDS = "5";
    private static final String SEARCH_FOR_PRIDE_AND_PREJUDICE = "/search?q=pride";
    private static final String INGEST_STATUS = "/ingest/status/";
    private static final String LOCATION = "Location";
    private static final String STATUS = "status";
    private static final String ROOT = "/";
    private static final int CONSECUTIVE_ANSWERS = 10;
    private static final int OK = 200;
    private static final int ACCEPTED = 202;

    private record PendingBook(int bookId, String statusPath) {
    }

    private final Api api;

    PublicationWait(Api api) {
        this.api = api;
    }

    void ensurePublished(List<Integer> sampleIds) throws IOException, InterruptedException {
        LOGGER.info("Ensuring all sample books are published");
        List<PendingBook> pendingBooks = new ArrayList<>();
        for (int bookId : sampleIds) {
            ingest(bookId).ifPresent(pendingBooks::add);
        }
        for (PendingBook pendingBook : pendingBooks) {
            waitForPublished(pendingBook);
        }
        waitForEveryReplica(sampleIds);
    }

    /** The book to wait for after {@code POST /ingest}, or empty when it was already {@code PUBLISHED}. */
    private Optional<PendingBook> ingest(int bookId) throws IOException, InterruptedException {
        CheckedClient.Response response = api.postOperational("/ingest/" + bookId);
        assertTrue(response.status() == OK || response.status() == ACCEPTED);
        if (response.status() == ACCEPTED) {
            String location = response.header(LOCATION);
            assertTrue(location != null && !location.isBlank());
            return Optional.of(new PendingBook(bookId, location.startsWith(ROOT) ? location : ROOT + location));
        }
        boolean published = BookStatus.PUBLISHED.name().equals(response.json().get(STATUS).asText());
        return published ? Optional.empty() : Optional.of(new PendingBook(bookId, INGEST_STATUS + bookId));
    }

    private void waitForPublished(PendingBook pendingBook) throws IOException, InterruptedException {
        Instant deadline = Instant.now().plus(MAX_WAIT);
        while (Instant.now().isBefore(deadline)) {
            CheckedClient.Response response = api.operational(pendingBook.statusPath());
            String status = response.status() == OK && response.json() != null && response.json().has(STATUS)
                    ? response.json().get(STATUS).asText()
                    : "";
            if (BookStatus.PUBLISHED.name().equals(status)) {
                return;
            }
            if (BookStatus.FAILED.name().equals(status)) {
                String reason = response.json().has("last_error") ? response.json().get("last_error").asText() : "unknown";
                throw new AssertionError("Book " + pendingBook.bookId() + " failed ingestion: " + reason);
            }
            Thread.sleep(STATUS_POLL);
        }
        throw new AssertionError("Timed out waiting for book " + pendingBook.bookId() + " to reach PUBLISHED at " + pendingBook.statusPath());
    }

    private void waitForEveryReplica(List<Integer> sampleIds) throws IOException, InterruptedException {
        Duration refresh = Duration.ofSeconds(Integer.parseInt(System.getenv().getOrDefault(SNAPSHOT_SECONDS, DEFAULT_SNAPSHOT_SECONDS)))
                .plus(REFRESH_MARGIN);
        waitForFilters(sampleIds.size());
        for (int bookId : sampleIds) {
            waitForBook(bookId, refresh);
        }
        waitForSearch(refresh);
    }

    /** Ten answers in a row count every sample book, so nginx has sent requests to every replica. */
    private void waitForFilters(int books) throws InterruptedException {
        Instant deadline = Instant.now().plus(MAX_WAIT);
        int consecutiveAnswers = 0;
        while (Instant.now().isBefore(deadline) && consecutiveAnswers < CONSECUTIVE_ANSWERS) {
            boolean countsEveryBook = answer("/search/filters")
                    .filter(response -> response.json().has("total_books") && response.json().get("total_books").asInt() >= books)
                    .isPresent();
            consecutiveAnswers = countsEveryBook ? consecutiveAnswers + 1 : 0;
            Thread.sleep(ANSWER_POLL);
        }
        if (consecutiveAnswers < CONSECUTIVE_ANSWERS) {
            throw new AssertionError("Snapshot not refreshed on all replicas: search/filters did not return total_books >= "
                    + books + " across 10 consecutive requests");
        }
    }

    private void waitForBook(int bookId, Duration refresh) throws IOException, InterruptedException {
        Instant deadline = Instant.now().plus(refresh);
        while (Instant.now().isBefore(deadline)) {
            if (api.frontEnd("/books/" + bookId).status() == OK) {
                return;
            }
            Thread.sleep(BOOK_POLL);
        }
        throw new AssertionError("Book " + bookId + " not visible in /books/" + bookId + " within snapshot refresh");
    }

    private void waitForSearch(Duration refresh) throws InterruptedException {
        Instant deadline = Instant.now().plus(refresh);
        while (Instant.now().isBefore(deadline)) {
            boolean found = answer(SEARCH_FOR_PRIDE_AND_PREJUDICE)
                    .filter(response -> containsBook(response.json().get("results"), PRIDE_AND_PREJUDICE))
                    .isPresent();
            if (found) {
                return;
            }
            Thread.sleep(ANSWER_POLL);
        }
        throw new AssertionError("Book " + PRIDE_AND_PREJUDICE + " not found by " + SEARCH_FOR_PRIDE_AND_PREJUDICE + " within snapshot refresh");
    }

    /** A {@code 200} with a JSON body from the front-end port, or empty when there is none yet. */
    private Optional<CheckedClient.Response> answer(String path) throws InterruptedException {
        try {
            CheckedClient.Response response = api.frontEnd(path);
            return response.status() == OK && response.json() != null ? Optional.of(response) : Optional.empty();
        } catch (IOException notYetReachable) {
            return Optional.empty();
        }
    }
}
