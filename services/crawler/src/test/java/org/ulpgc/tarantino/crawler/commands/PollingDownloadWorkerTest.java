package org.ulpgc.tarantino.crawler.commands;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.common.config.CrashPoint;
import org.ulpgc.tarantino.common.model.BookErrorCode;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.crawler.adapters.datalake.batch.BatchBasedDatalakeAdapter;
import org.ulpgc.tarantino.crawler.model.ClaimedBook;
import org.ulpgc.tarantino.crawler.ports.BookClaims;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PollingDownloadWorkerTest {

    private static final ClaimedBook CLAIMED = new ClaimedBook(1342, "claim-1", Instant.parse("2026-10-21T10:05:00Z"));

    @TempDir
    Path datalake;

    private final List<CrashPoint> reachedPoints = new ArrayList<>();
    private final List<Integer> failedBooks = new ArrayList<>();
    private int polls;

    @AfterEach
    void clearInterruption() {
        Thread.interrupted();
    }

    @Test
    void downloadsEachClaimedBookAndPollsAgainUntilNoneWaits() {
        DownloadBookCommand download = new DownloadBookCommand(new PollingClaims(),
                new IngestBookCommand(bookId -> "", new BatchBasedDatalakeAdapter(datalake)), task -> {
        }, task -> {
        }, new TransitionLog(), Clock.systemUTC(), reachedPoints::add, new RecordedLease()::start);

        new PollingDownloadWorker(new PollingClaims(), download, Duration.ofMillis(1)).run();

        assertEquals(List.of(1342), failedBooks);
        assertEquals(List.of(CrashPoint.AFTER_CLAIM), reachedPoints);
        assertEquals(2, polls);
    }

    private final class PollingClaims implements BookClaims {

        @Override
        public Optional<ClaimedBook> claimNext() {
            polls++;
            if (polls > 1) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
            return Optional.of(CLAIMED);
        }

        @Override
        public Optional<String> claim(int bookId) {
            return Optional.empty();
        }

        @Override
        public boolean renew(int bookId, String claimId) {
            return true;
        }

        @Override
        public boolean finish(int bookId, String claimId) {
            return false;
        }

        @Override
        public Optional<BookStatus> fail(int bookId, String claimId, BookErrorCode reason) {
            failedBooks.add(bookId);
            return Optional.of(BookStatus.FAILED);
        }
    }
}
