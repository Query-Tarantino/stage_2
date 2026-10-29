package org.ulpgc.tarantino.bench.coordination;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BookCoordinationTest {

    private static final Instant START = Instant.parse("2026-10-21T10:00:00Z");

    @Test
    void measuresEachPhaseUntilItsBatchIsPublishedWhateverTheOrderOfTheLogs() {
        List<TransitionLine> lines = new ArrayList<>(List.of(
                book(84, null, "QUEUED", null, null, 0),
                book(84, "QUEUED", "DOWNLOADING", null, 1, 3),
                book(84, "DOWNLOADING", "DOWNLOADED", null, null, 10),
                book(84, "DOWNLOADED", "INDEXING", "b1", 10, 12),
                batch("b1", "WRITING", "PUBLISHED", 20),
                book(84, "INDEXING", "PUBLISHED", "b1", null, 21)));
        Collections.shuffle(lines, new Random(7));

        assertEquals(List.of(new BookCoordination(84, seconds(2), seconds(7), seconds(2), seconds(8), seconds(20))),
                BookCoordination.of(lines));
    }

    @Test
    void measuresTheClaimsThatSucceededAndCountsTheRetriesInTheTotal() {
        List<TransitionLine> lines = List.of(
                book(1342, null, "QUEUED", null, null, 0),
                book(1342, "QUEUED", "DOWNLOADING", null, 0, 1),
                book(1342, "DOWNLOADING", "QUEUED", null, null, 20),
                book(1342, "QUEUED", "QUEUED", null, null, 30),
                book(1342, "QUEUED", "DOWNLOADING", null, 30, 31),
                book(1342, "DOWNLOADING", "DOWNLOADED", null, null, 35),
                book(1342, "DOWNLOADED", "INDEXING", "abandoned", 35, 36),
                batch("abandoned", "WRITING", "ABANDONED", 50),
                book(1342, "INDEXING", "DOWNLOADED", "abandoned", null, 51),
                book(1342, "DOWNLOADED", "INDEXING", "published", 51, 55),
                batch("published", "WRITING", "PUBLISHED", 60));

        assertEquals(List.of(new BookCoordination(1342, seconds(1), seconds(4), seconds(4), seconds(5), seconds(60))),
                BookCoordination.of(lines));
    }

    @Test
    void leavesOutTheBooksThatWereNeverPublished() {
        List<TransitionLine> lines = List.of(
                book(11, null, "QUEUED", null, null, 0),
                book(11, "QUEUED", "DOWNLOADING", null, 0, 1),
                book(11, "DOWNLOADING", "FAILED", null, null, 2),
                book(84, null, "QUEUED", null, null, 0),
                book(84, "QUEUED", "DOWNLOADING", null, 0, 1),
                book(84, "DOWNLOADING", "DOWNLOADED", null, null, 2),
                book(84, "DOWNLOADED", "INDEXING", "writing", 2, 3));

        assertEquals(List.of(), BookCoordination.of(lines));
    }

    private static TransitionLine book(int bookId, String from, String to, String batchId, Integer enqueuedSecond, int second) {
        Instant enqueuedAt = enqueuedSecond == null ? null : START.plusSeconds(enqueuedSecond);
        return new TransitionLine(TransitionLine.BOOK, String.valueOf(bookId), from, to, batchId, enqueuedAt, START.plusSeconds(second));
    }

    private static TransitionLine batch(String batchId, String from, String to, int second) {
        return new TransitionLine(TransitionLine.BATCH, batchId, from, to, null, null, START.plusSeconds(second));
    }

    private static Duration seconds(int seconds) {
        return Duration.ofSeconds(seconds);
    }
}
