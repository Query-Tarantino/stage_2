package org.ulpgc.tarantino.checks;

import org.ulpgc.tarantino.common.config.TarantinoConfig;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.ulpgc.tarantino.checks.ApiNames.BOOKS_PATH;
import static org.ulpgc.tarantino.checks.ApiNames.RESULTS;
import static org.ulpgc.tarantino.checks.ApiNames.SEARCH;
import static org.ulpgc.tarantino.checks.ApiNames.SEARCH_FILTERS;
import static org.ulpgc.tarantino.checks.ApiNames.TOTAL_BOOKS;
import static org.ulpgc.tarantino.checks.SampleBooks.PRIDE_AND_PREJUDICE;

/**
 * Waits until every {@code query} replica serves the published sample books from its snapshot, which each one
 * refreshes on its own every {@code TARANTINO_SNAPSHOT_SECONDS} (SPEC §7), so the later checks see all of them.
 */
final class SnapshotRefreshWait {

    private static final Duration MAX_WAIT = Duration.ofSeconds(60);
    private static final Duration COUNT_POLL = Duration.ofMillis(200);
    private static final Duration BOOK_POLL = Duration.ofMillis(500);
    private static final Duration SEARCH_POLL = Duration.ofMillis(200);
    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(2);
    private static final int CONSECUTIVE_COUNTS = 10;
    private static final String PRIDE = SEARCH + "?q=pride";

    @FunctionalInterface
    private interface Probe {
        boolean holds() throws IOException, InterruptedException;
    }

    private final CheckedClient client;
    private final String frontEndUrl;

    SnapshotRefreshWait(CheckedClient client, String frontEndUrl) {
        this.client = client;
        this.frontEndUrl = frontEndUrl;
    }

    void waitUntilServed(List<Integer> sampleIds) throws IOException, InterruptedException {
        long refreshMillis = snapshotRefresh().toMillis();
        waitForEveryReplicaToCount(sampleIds.size());
        for (int bookId : sampleIds) {
            waitUntil(() -> client.get(frontEndUrl + BOOKS_PATH + bookId).status() == HTTP_OK, refreshMillis, BOOK_POLL,
                    "Book " + bookId + " not visible in /books/" + bookId + " within snapshot refresh");
        }
        waitUntil(this::searchFindsPrideAndPrejudice, refreshMillis, SEARCH_POLL,
                "Book " + PRIDE_AND_PREJUDICE + " not found by " + PRIDE + " within snapshot refresh");
    }

    private static Duration snapshotRefresh() {
        String seconds = System.getenv().getOrDefault(TarantinoConfig.SNAPSHOT_SECONDS, String.valueOf(TarantinoConfig.DEFAULT_SNAPSHOT_SECONDS));
        return Duration.ofSeconds(Integer.parseInt(seconds)).plus(REFRESH_MARGIN);
    }

    /** nginx sends each request to the next replica, so enough answers in a row that count every book cover all of them. */
    private void waitForEveryReplicaToCount(int books) throws InterruptedException {
        long deadline = System.currentTimeMillis() + MAX_WAIT.toMillis();
        int consecutive = 0;
        while (System.currentTimeMillis() < deadline && consecutive < CONSECUTIVE_COUNTS) {
            consecutive = countsEveryBook(books) ? consecutive + 1 : 0;
            Thread.sleep(COUNT_POLL);
        }
        if (consecutive < CONSECUTIVE_COUNTS) {
            throw new AssertionError("Snapshot not refreshed on all replicas: search/filters did not return total_books >= "
                    + books + " across " + CONSECUTIVE_COUNTS + " consecutive requests");
        }
    }

    private boolean countsEveryBook(int books) throws InterruptedException {
        try {
            CheckedClient.Response response = client.get(frontEndUrl + SEARCH_FILTERS);
            return response.status() == HTTP_OK && response.json() != null && response.json().has(TOTAL_BOOKS)
                    && response.json().get(TOTAL_BOOKS).asInt() >= books;
        } catch (IOException exception) {
            return false;
        }
    }

    private boolean searchFindsPrideAndPrejudice() throws InterruptedException {
        try {
            CheckedClient.Response response = client.get(frontEndUrl + PRIDE);
            return response.status() == HTTP_OK && response.json() != null
                    && SearchResults.containsBook(response.json().get(RESULTS), PRIDE_AND_PREJUDICE);
        } catch (IOException exception) {
            return false;
        }
    }

    private static void waitUntil(Probe probe, long timeoutMillis, Duration poll, String failure) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (probe.holds()) {
                return;
            }
            Thread.sleep(poll);
        }
        throw new AssertionError(failure);
    }
}
