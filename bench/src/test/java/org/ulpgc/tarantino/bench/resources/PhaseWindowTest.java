package org.ulpgc.tarantino.bench.resources;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.bench.coordination.TransitionLine;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhaseWindowTest {

    @Test
    void spansIngestionFromTheFirstDownloadClaimToTheLastDownloadAndIndexingUpToTheLastBatchPublished() {
        List<TransitionLine> lines = Stream.of(
                        book(84, "-", "QUEUED", "10:00:00Z"),
                        book(84, "QUEUED", "DOWNLOADING", "10:00:01Z"),
                        book(1342, "QUEUED", "DOWNLOADING", "10:00:02Z"),
                        book(84, "DOWNLOADING", "DOWNLOADED", "10:00:05Z"),
                        book(1342, "DOWNLOADING", "DOWNLOADED", "10:00:09Z"),
                        book(84, "DOWNLOADED", "INDEXING", "10:00:20Z"),
                        batch("b1", "WRITING", "PUBLISHED", "10:00:31Z"),
                        book(84, "INDEXING", "PUBLISHED", "10:00:32Z"))
                .toList();

        assertEquals(List.of(
                        new PhaseWindow(PhaseWindow.INGESTION, at("10:00:01Z"), at("10:00:09Z")),
                        new PhaseWindow(PhaseWindow.INDEXING, at("10:00:20Z"), at("10:00:31Z"))),
                PhaseWindow.of(lines));
    }

    @Test
    void leavesOutAPhaseThatNeverEnded() {
        List<TransitionLine> lines = List.of(book(84, "QUEUED", "DOWNLOADING", "10:00:01Z"));

        assertEquals(List.of(), PhaseWindow.of(lines));
    }

    @Test
    void containsBothEnds() {
        PhaseWindow window = new PhaseWindow(PhaseWindow.INGESTION, at("10:00:01Z"), at("10:00:09Z"));

        assertTrue(window.contains(at("10:00:01Z")));
        assertTrue(window.contains(at("10:00:09Z")));
        assertFalse(window.contains(at("10:00:09.001Z")));
    }

    private static TransitionLine book(int bookId, String from, String to, String time) {
        return TransitionLine.parse("transition entity=book id=" + bookId + " from=" + from + " to=" + to
                + " batch_id=- generation=- enqueued_at=- at=2026-10-21T" + time + " replica=r").orElseThrow();
    }

    private static TransitionLine batch(String batchId, String from, String to, String time) {
        return TransitionLine.parse("transition entity=batch id=" + batchId + " from=" + from + " to=" + to
                + " batch_id=- generation=1 enqueued_at=- at=2026-10-21T" + time + " replica=r").orElseThrow();
    }

    private static Instant at(String time) {
        return Instant.parse("2026-10-21T" + time);
    }
}
