package org.ulpgc.tarantino.crawler.commands;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.common.config.CrashPoint;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.crawler.adapters.datalake.batch.BatchBasedDatalakeAdapter;
import org.ulpgc.tarantino.crawler.ports.BookClaims;
import org.ulpgc.tarantino.crawler.ports.DownloadQueue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DownloadWorkerTest {

    private static final BookTask TASK = new BookTask(1342, Instant.parse("2026-10-21T10:05:00Z"));

    @TempDir
    Path datalake;

    private final List<Integer> claimedBooks = new ArrayList<>();
    private final List<CrashPoint> reachedPoints = new ArrayList<>();

    @AfterEach
    void clearInterruption() {
        Thread.interrupted();
    }

    @Test
    void passesAfterDequeueBeforeClaimingTheMessage() {
        worker(reachedPoints::add).run();

        assertEquals(List.of(CrashPoint.CRAWLER_AFTER_DEQUEUE), reachedPoints);
        assertEquals(List.of(1342), claimedBooks);
    }

    @Test
    void stopsAfterDequeueWithoutClaimingTheMessage() {
        Consumer<CrashPoint> haltAfterDequeue = point -> {
            throw new Halted();
        };

        assertThrows(Halted.class, () -> worker(haltAfterDequeue).run());

        assertEquals(List.of(), claimedBooks);
    }

    private DownloadWorker worker(Consumer<CrashPoint> crashPoints) {
        DownloadBookCommand download = new DownloadBookCommand(new RecordingClaims(),
                new IngestBookCommand(bookId -> "", new BatchBasedDatalakeAdapter(datalake)), task -> {
        }, new TransitionLog(), Clock.systemUTC(), crashPoints);
        return new DownloadWorker(oneMessageThenStop(), download, crashPoints);
    }

    private static DownloadQueue oneMessageThenStop() {
        List<BookTask> messages = new ArrayList<>(List.of(TASK));
        return wait -> {
            if (messages.isEmpty()) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
            return Optional.of(messages.removeFirst());
        };
    }

    private final class RecordingClaims implements BookClaims {

        @Override
        public Optional<String> claim(int bookId) {
            claimedBooks.add(bookId);
            return Optional.empty();
        }

        @Override
        public boolean finish(int bookId, String claimId) {
            return false;
        }
    }

    private static final class Halted extends Error {
    }
}
