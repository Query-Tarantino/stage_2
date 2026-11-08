package org.ulpgc.tarantino.bench.ingest;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkerTimesTest {

    private static final Instant START = Instant.parse("2026-11-05T22:01:21Z");

    @Test
    void measuresEachWorkerOnItsOwnThreadAndReplica() {
        List<WorkerLine> lines = parsed(
                line("a", "download-0", 1, "QUEUED", "DOWNLOADING", 0),
                line("a", "download-1", 2, "QUEUED", "DOWNLOADING", 1),
                line("a", "download-0", 1, "DOWNLOADING", "DOWNLOADED", 10),
                line("b", "download-0", 3, "QUEUED", "DOWNLOADING", 11),
                line("a", "download-0", 4, "QUEUED", "DOWNLOADING", 12),
                line("a", "download-1", 2, "DOWNLOADING", "DOWNLOADED", 21),
                line("b", "download-0", 3, "DOWNLOADING", "DOWNLOADED", 26),
                line("a", "download-0", 4, "DOWNLOADING", "DOWNLOADED", 30));

        WorkerTimes times = WorkerTimes.of(lines).orElseThrow();

        assertEquals(List.of(10.0, 18.0, 20.0, 15.0), times.workMillis());
        assertEquals(List.of(2.0), times.gapMillis());
    }

    @Test
    void leavesOutAClaimThatNeverEndedInADownload() {
        List<WorkerLine> lines = parsed(
                line("a", "download-0", 1, "QUEUED", "DOWNLOADING", 0),
                line("a", "download-0", 1, "DOWNLOADING", "QUEUED", 5),
                line("a", "download-0", 2, "QUEUED", "DOWNLOADING", 6),
                line("a", "download-0", 2, "DOWNLOADING", "DOWNLOADED", 9));

        WorkerTimes times = WorkerTimes.of(lines).orElseThrow();

        assertEquals(List.of(3.0), times.workMillis());
        assertEquals(List.of(), times.gapMillis());
    }

    @Test
    void hasNothingToMeasureWithoutADownload() {
        assertEquals(Optional.empty(), WorkerTimes.of(parsed(line("a", "download-0", 1, "QUEUED", "DOWNLOADING", 0))));
    }

    @Test
    void readsTheWorkerFromTheThreadAndTheReplicaOfTheLine() {
        WorkerLine parsed = WorkerLine.parse(line("63867007e4c7", "download-7", 1342, "QUEUED", "DOWNLOADING", 0)).orElseThrow();

        assertEquals("63867007e4c7/download-7", parsed.worker());
        assertEquals("1342", parsed.transition().id());
    }

    private static List<WorkerLine> parsed(String... lines) {
        return Stream.of(lines).flatMap(line -> WorkerLine.parse(line).stream()).toList();
    }

    static String line(String replica, String thread, int bookId, String from, String to, long millis) {
        Instant at = START.plusMillis(millis);
        return "crawler-1  | " + at + " [" + thread + "] INFO org.ulpgc.tarantino.common.operations.TransitionLog - transition"
                + " entity=book id=" + bookId + " from=" + from + " to=" + to + " batch_id=- generation=- enqueued_at=- at=" + at
                + " replica=" + replica;
    }
}
