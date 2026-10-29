package org.ulpgc.tarantino.bench.containers;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContainerStatsTest {

    private static final Instant AT = Instant.parse("2026-10-21T10:00:01Z");

    @Test
    void readsTheCpuAndTheMemoryInUseOfASample() {
        String line = """
                {"BlockIO":"0B / 0B","CPUPerc":"112.47%","Container":"9f1c","ID":"9f1c","MemPerc":"6.85%","MemUsage":"512.5MiB / 7.47GiB","Name":"stage_2-indexer-2","NetIO":"1kB / 0B","PIDs":"31"}""";

        ContainerStats stats = ContainerStats.parse(line, AT).orElseThrow();

        assertEquals(new ContainerStats(AT, "stage_2-indexer-2", 112.47, 537_395_200L), stats);
        assertEquals("2026-10-21T10:00:01Z,stage_2-indexer-2,112.47,537395200", stats.csvRow());
    }

    @Test
    void readsASampleBehindTheCodesThatClearTheScreenAndDecimalUnits() {
        String line = "\u001B[2J\u001B[H{\"CPUPerc\":\"0.31%\",\"MemUsage\":\"1.5GB / 8GB\",\"Name\":\"stage_2-mongo-1\"}";

        assertEquals(Optional.of(new ContainerStats(AT, "stage_2-mongo-1", 0.31, 1_500_000_000L)), ContainerStats.parse(line, AT));
    }

    @Test
    void readsBackTheRowItWrites() {
        ContainerStats stats = new ContainerStats(AT, "stage_2-crawler-3", 47.5, 268_435_456L);

        assertEquals(stats, ContainerStats.fromCsvRow(stats.csvRow()));
    }

    @Test
    void skipsWhatIsNotASample() {
        assertEquals(Optional.empty(), ContainerStats.parse("\u001B[2J\u001B[H", AT));
        assertEquals(Optional.empty(), ContainerStats.parse("{\"CPUPerc\":\"--\",\"MemUsage\":\"-- / --\",\"Name\":\"stage_2-crawler-1\"}", AT));
        assertEquals(Optional.empty(), ContainerStats.parse("{not json}", AT));
    }
}
