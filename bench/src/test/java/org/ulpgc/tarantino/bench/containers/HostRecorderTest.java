package org.ulpgc.tarantino.bench.containers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostRecorderTest {

    private static final Duration PERIOD = Duration.ofMillis(10);
    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final HostStats SAMPLE = new HostStats(Instant.parse("2026-11-05T10:00:00Z"), 150.0, 1_048_576);

    @TempDir
    Path runDirectory;

    @Test
    void writesTheHeaderOnceAndARowOnEveryTick() throws IOException, InterruptedException {
        Path csv = runDirectory.resolve(HostStats.FILE_NAME);

        record(csv, 3);
        int afterTheFirst = Files.readAllLines(csv).size();
        record(csv, afterTheFirst + 2);

        List<String> lines = Files.readAllLines(csv);
        assertEquals(HostStats.CSV_HEADER, lines.getFirst());
        assertEquals(1, lines.stream().filter(HostStats.CSV_HEADER::equals).count());
        assertTrue(lines.size() >= afterTheFirst + 2);
        lines.stream().skip(1).forEach(row -> assertEquals(SAMPLE, HostStats.fromCsvRow(row)));
    }

    @Test
    void skipsTheTicksWithoutASample() throws IOException, InterruptedException {
        Path csv = runDirectory.resolve(HostStats.FILE_NAME);

        try (HostRecorder ignored = HostRecorder.start(csv, PERIOD, Optional::empty)) {
            Thread.sleep(PERIOD.multipliedBy(5));
        }

        assertEquals(List.of(HostStats.CSV_HEADER), Files.readAllLines(csv));
    }

    private static void record(Path csv, int lines) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        try (HostRecorder ignored = HostRecorder.start(csv, PERIOD, () -> Optional.of(SAMPLE))) {
            while (Files.readAllLines(csv).size() < lines && System.nanoTime() < deadline) {
                Thread.sleep(PERIOD);
            }
        }
    }
}
