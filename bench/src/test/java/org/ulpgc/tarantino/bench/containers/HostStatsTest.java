package org.ulpgc.tarantino.bench.containers;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HostStatsTest {

    private static final Instant AT = Instant.parse("2026-11-05T10:00:00Z");

    @Test
    void countsTheCpuInPercentOfOneCoreAndTheMemoryInUse() {
        HostStats sample = HostStats.of(AT, 0.25, 8, 16_000, 6_000).orElseThrow();

        assertEquals(200.0, sample.cpuPercent());
        assertEquals(10_000, sample.memoryUsedBytes());
    }

    @Test
    void hasNoSampleWhileTheSystemCannotGiveTheCpuLoad() {
        assertEquals(Optional.empty(), HostStats.of(AT, -1, 8, 16_000, 6_000));
    }

    @Test
    void readsBackTheRowItWrites() {
        HostStats sample = new HostStats(AT, 412.5, 8_589_934_592L);

        assertEquals("2026-11-05T10:00:00Z,412.50,8589934592", sample.csvRow());
        assertEquals(sample, HostStats.fromCsvRow(sample.csvRow()));
    }
}
