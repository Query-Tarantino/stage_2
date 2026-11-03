package org.ulpgc.tarantino.bench.index;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.bench.coordination.TransitionLine;
import org.ulpgc.tarantino.bench.throughput.ThroughputWindow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndexThroughputTest {

    private static final Instant START = Instant.parse("2026-11-02T18:00:00Z");
    private static final int FIRST_GENERATION = 1;
    private static final int REBUILT_GENERATION = 2;

    @Test
    void measuresFromTheFirstIndexClaimToTheLastBatchPublished() {
        List<TransitionLine> lines = List.of(
                claimed("1", REBUILT_GENERATION, 2), claimed("2", REBUILT_GENERATION, 3),
                batchPublished(REBUILT_GENERATION, 12), published("1", REBUILT_GENERATION, 12), published("2", REBUILT_GENERATION, 12));

        ThroughputWindow window = IndexThroughput.of(lines).orElseThrow();

        assertEquals(new ThroughputWindow(2, 10.0), window);
        assertEquals(0.2, window.booksPerSecond());
    }

    @Test
    void readsOnlyTheHighestGenerationWhenTheLogsStillHoldAnEarlierOne() {
        List<TransitionLine> lines = List.of(
                claimed("1", FIRST_GENERATION, 0), claimed("2", FIRST_GENERATION, 0), claimed("3", FIRST_GENERATION, 0),
                batchPublished(FIRST_GENERATION, 1), published("1", FIRST_GENERATION, 1), published("2", FIRST_GENERATION, 1), published("3", FIRST_GENERATION, 1),
                book("1", "PUBLISHED", "DOWNLOADED", null, 40),
                claimed("1", REBUILT_GENERATION, 50), batchPublished(REBUILT_GENERATION, 55), published("1", REBUILT_GENERATION, 55));

        assertEquals(new ThroughputWindow(1, 5.0), IndexThroughput.of(lines).orElseThrow());
    }

    @Test
    void countsOnceABookPublishedTwiceAndIgnoresABatchLeftUnpublished() {
        List<TransitionLine> lines = List.of(
                claimed("1", REBUILT_GENERATION, 0), batchPublished(REBUILT_GENERATION, 4), published("1", REBUILT_GENERATION, 4),
                published("1", REBUILT_GENERATION, 5), batch("WRITING", "ABANDONED", REBUILT_GENERATION, 9));

        assertEquals(new ThroughputWindow(1, 4.0), IndexThroughput.of(lines).orElseThrow());
    }

    @Test
    void hasNoThroughputWithoutAPublishedBatchOrWithoutTimeBetweenItsEnds() {
        assertEquals(Optional.empty(), IndexThroughput.of(List.of(claimed("1", REBUILT_GENERATION, 0))));
        assertEquals(Optional.empty(), IndexThroughput.of(List.of(
                claimed("1", REBUILT_GENERATION, 3), batchPublished(REBUILT_GENERATION, 3), published("1", REBUILT_GENERATION, 3))));
    }

    private static TransitionLine claimed(String bookId, int generation, int second) {
        return book(bookId, "DOWNLOADED", "INDEXING", generation, second);
    }

    private static TransitionLine published(String bookId, int generation, int second) {
        return book(bookId, "INDEXING", "PUBLISHED", generation, second);
    }

    private static TransitionLine batchPublished(int generation, int second) {
        return batch("WRITING", "PUBLISHED", generation, second);
    }

    private static TransitionLine book(String bookId, String from, String to, Integer generation, int second) {
        return new TransitionLine(TransitionLine.BOOK, bookId, from, to, null, generation, null, START.plusSeconds(second));
    }

    private static TransitionLine batch(String from, String to, int generation, int second) {
        return new TransitionLine(TransitionLine.BATCH, "batch-" + generation, from, to, null, generation, null, START.plusSeconds(second));
    }
}
