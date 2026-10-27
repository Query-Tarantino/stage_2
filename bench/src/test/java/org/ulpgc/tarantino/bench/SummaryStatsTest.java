package org.ulpgc.tarantino.bench;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummaryStatsTest {

    @Test
    void shouldCalculateMetricsCorrectly() {
        List<Long> values = List.of(100L, 200L, 300L, 400L, 500L);
        SummaryStats.Metrics metrics = SummaryStats.calculate(values);

        assertEquals(5, metrics.count());
        assertEquals(300.0, metrics.mean(), 0.001);
        assertEquals(300L, metrics.p50());
        assertEquals(500L, metrics.p95());
        assertEquals(500L, metrics.p99());
        assertTrue(metrics.stdDev() > 0.0);
        assertTrue(metrics.ci95() > 0.0);
    }

    @Test
    void shouldAppendSummaryCsv(@TempDir Path tempDir) throws IOException {
        Path summaryCsv = tempDir.resolve("summary.csv");
        SummaryStats.Metrics m = new SummaryStats.Metrics(10, 150.0, 12.0, 7.5, 148, 170, 180);
        SummaryStats.appendToSummary(summaryCsv, "search_latency", "clients=4", m, "us");

        assertTrue(Files.exists(summaryCsv));
        List<String> lines = Files.readAllLines(summaryCsv);
        assertEquals(2, lines.size());
        assertEquals("metric,configuration,mean,std_dev,ci_95,p50,p95,p99,unit", lines.getFirst());
        assertTrue(lines.get(1).startsWith("search_latency,\"clients=4\",150.00,12.00,7.50,148,170,180,us"));
    }
}
