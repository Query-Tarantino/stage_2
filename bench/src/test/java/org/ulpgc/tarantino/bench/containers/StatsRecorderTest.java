package org.ulpgc.tarantino.bench.containers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsRecorderTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-10-21T10:00:01Z"), ZoneOffset.UTC);
    private static final String CLEAR_SCREEN = "\u001B[2J\u001B[H";

    @TempDir
    Path runDirectory;

    @Test
    void writesTheHeaderAndARowPerContainerSample() {
        Path csv = runDirectory.resolve("stats.csv");
        String streamed = CLEAR_SCREEN + sample("stage_2-crawler-1", "3.10%", "200MiB") + "\n"
                + sample("stage_2-mongo-1", "45.00%", "1GiB") + "\n";

        long written = new StatsRecorder(FIXED).record(reader(streamed), csv);

        assertEquals(2, written);
        assertEquals(List.of(ContainerStats.CSV_HEADER,
                "2026-10-21T10:00:01Z,stage_2-crawler-1,3.10,209715200",
                "2026-10-21T10:00:01Z,stage_2-mongo-1,45.00,1073741824"), lines(csv));
    }

    @Test
    void appendsToARecordingThatWasStoppedWithoutRepeatingTheHeader() {
        Path csv = runDirectory.resolve("stats.csv");
        new StatsRecorder(FIXED).record(reader(sample("stage_2-query-1", "1.00%", "100MiB") + "\n"), csv);

        new StatsRecorder(FIXED).record(reader(sample("stage_2-query-1", "2.00%", "100MiB") + "\n"), csv);

        assertEquals(3, lines(csv).size());
        assertEquals(ContainerStats.CSV_HEADER, lines(csv).getFirst());
    }

    private static String sample(String container, String cpu, String memory) {
        return "{\"CPUPerc\":\"" + cpu + "\",\"MemUsage\":\"" + memory + " / 7.47GiB\",\"Name\":\"" + container + "\"}";
    }

    private static BufferedReader reader(String streamed) {
        return new BufferedReader(new StringReader(streamed));
    }

    private static List<String> lines(Path csv) {
        try {
            return Files.readAllLines(csv);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
