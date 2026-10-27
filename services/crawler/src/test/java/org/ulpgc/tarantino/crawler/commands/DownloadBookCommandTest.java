package org.ulpgc.tarantino.crawler.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.common.config.CrashPoint;
import org.ulpgc.tarantino.common.model.BookErrorCode;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.crawler.adapters.datalake.batch.BatchBasedDatalakeAdapter;
import org.ulpgc.tarantino.crawler.model.failure.DownloadException;
import org.ulpgc.tarantino.crawler.ports.BookClaims;
import org.ulpgc.tarantino.crawler.ports.BookDownloader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadBookCommandTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:05:03Z");
    private static final BookTask TASK = new BookTask(1342, Instant.parse("2026-10-21T10:05:00Z"));
    private static final String RAW = "Title: Pride and Prejudice\n*** START OF THE PROJECT GUTENBERG EBOOK PRIDE ***\nbody\n"
            + "*** END OF THE PROJECT GUTENBERG EBOOK PRIDE ***";

    @TempDir
    Path datalake;

    private final FakeClaims claims = new FakeClaims();
    private final List<BookTask> indexQueue = new ArrayList<>();
    private final List<CrashPoint> reachedPoints = new ArrayList<>();

    @Test
    void storesAClaimedBookAndEnqueuesItForIndexing() {
        claims.queued.add(1342);

        command(bookId -> RAW).execute(TASK);

        assertTrue(Files.exists(datalake.resolve("1/1342.body.txt")));
        assertEquals(Set.of(1342), claims.finished);
        assertEquals(List.of(new BookTask(1342, NOW)), indexQueue);
    }

    @Test
    void dropsAMessageWhoseClaimFails() {
        command(bookId -> RAW).execute(TASK);

        assertFalse(Files.exists(datalake.resolve("1/1342.body.txt")));
        assertEquals(List.of(), indexQueue);
        assertEquals(List.of(), reachedPoints);
    }

    @Test
    void neitherFinishesNorEnqueuesAFailedDownload() {
        claims.queued.add(1342);
        BookDownloader missing = bookId -> {
            throw new DownloadException(BookErrorCode.NOT_FOUND, "missing");
        };

        command(missing).execute(TASK);

        assertEquals(Set.of(), claims.finished);
        assertEquals(List.of(), indexQueue);
    }

    @Test
    void doesNotEnqueueABookWhoseLeaseWasLost() {
        claims.queued.add(1342);
        claims.leaseLost = true;

        command(bookId -> RAW).execute(TASK);

        assertEquals(List.of(), indexQueue);
    }

    @Test
    void passesTheCrashPointsOfAFinishedDownloadInOrder() {
        claims.queued.add(1342);

        command(bookId -> RAW).execute(TASK);

        assertEquals(List.of(CrashPoint.AFTER_CLAIM, CrashPoint.AFTER_DATALAKE_WRITE, CrashPoint.AFTER_DOWNLOADED), reachedPoints);
    }

    @Test
    void stopsAfterTheClaimBeforeWritingTheDatalake() {
        claims.queued.add(1342);

        assertThrows(Halted.class, () -> command(bookId -> RAW, haltAt(CrashPoint.AFTER_CLAIM)).execute(TASK));

        assertFalse(Files.exists(datalake.resolve("1/1342.header.txt")));
        assertEquals(Set.of(), claims.finished);
    }

    @Test
    void stopsAfterTheDatalakeWriteBeforeFinishingTheDownload() {
        claims.queued.add(1342);

        assertThrows(Halted.class, () -> command(bookId -> RAW, haltAt(CrashPoint.AFTER_DATALAKE_WRITE)).execute(TASK));

        assertTrue(Files.exists(datalake.resolve("1/1342.body.txt")));
        assertEquals(Set.of(), claims.finished);
    }

    @Test
    void stopsAfterTheFinishedDownloadBeforeEnqueuingItForIndexing() {
        claims.queued.add(1342);

        assertThrows(Halted.class, () -> command(bookId -> RAW, haltAt(CrashPoint.AFTER_DOWNLOADED)).execute(TASK));

        assertEquals(Set.of(1342), claims.finished);
        assertEquals(List.of(), indexQueue);
    }

    private DownloadBookCommand command(BookDownloader downloader) {
        return command(downloader, reachedPoints::add);
    }

    private DownloadBookCommand command(BookDownloader downloader, Consumer<CrashPoint> crashPoints) {
        IngestBookCommand ingest = new IngestBookCommand(downloader, new BatchBasedDatalakeAdapter(datalake));
        return new DownloadBookCommand(claims, ingest, indexQueue::add, new TransitionLog(), Clock.fixed(NOW, ZoneOffset.UTC),
                crashPoints);
    }

    private static Consumer<CrashPoint> haltAt(CrashPoint configured) {
        return point -> {
            if (point == configured) {
                throw new Halted();
            }
        };
    }

    private static final class Halted extends RuntimeException {
    }

    private static final class FakeClaims implements BookClaims {

        private final Set<Integer> queued = new HashSet<>();
        private final Set<Integer> finished = new HashSet<>();
        private boolean leaseLost;

        @Override
        public Optional<String> claim(int bookId) {
            return queued.remove(bookId) ? Optional.of("claim-" + bookId) : Optional.empty();
        }

        @Override
        public boolean finish(int bookId, String claimId) {
            return !leaseLost && ("claim-" + bookId).equals(claimId) && finished.add(bookId);
        }
    }
}
