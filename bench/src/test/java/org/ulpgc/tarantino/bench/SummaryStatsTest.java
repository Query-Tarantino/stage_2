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
        assertEquals(300.0, metrics.p50());
        assertEquals(500.0, metrics.p95());
        assertEquals(500.0, metrics.p99());
        assertTrue(metrics.stdDev() > 0.0);
        assertTrue(metrics.ci95() > 0.0);
    }

    @Test
    void keepsTheDecimalsAndTakesTheConfidenceIntervalFromStudentsT() {
        SummaryStats.Metrics metrics = SummaryStats.calculate(List.of(166.279, 77.395, 60.877));

        assertEquals(101.517, metrics.mean(), 0.001);
        assertEquals(77.395, metrics.p50());
        assertEquals(4.3027 * metrics.stdDev() / Math.sqrt(3), metrics.ci95(), 0.01);
    }

    @Test
    void shouldAppendSummaryCsv(@TempDir Path tempDir) throws IOException {
        Path summaryCsv = tempDir.resolve("summary.csv");
        SummaryStats.Metrics metrics = new SummaryStats.Metrics(10, 150.0, 12.0, 7.5, 148, 170, 180);
        SummaryStats.appendToSummary(summaryCsv, "search_latency", "clients=4", metrics, "us");

        assertTrue(Files.exists(summaryCsv));
        List<String> lines = Files.readAllLines(summaryCsv);
        assertEquals(2, lines.size());
        assertEquals("metric,configuration,mean,std_dev,ci_95,p50,p95,p99,unit", lines.getFirst());
        assertTrue(lines.get(1).startsWith("search_latency,\"clients=4\",150.00,12.00,7.50,148.00,170.00,180.00,us"));
    }

    @Test
    void shouldExecuteMainWithCustomMetricName(@TempDir Path tempDir) throws IOException {
        Path loadDir = tempDir.resolve("load");
        Files.createDirectories(loadDir);
        Path requestsCsv = loadDir.resolve("requests.csv");
        Files.writeString(requestsCsv, "instant,path,status,latency_us\n2026-10-27T12:00:00Z,/search,200,12345\n");

        SummaryStats.main(new String[]{tempDir.toString(), "series=clients,clients=32", requestsCsv.toString(), "custom_metric"});

        Path summaryCsv = tempDir.resolve("summary.csv");
        assertTrue(Files.exists(summaryCsv));
        List<String> lines = Files.readAllLines(summaryCsv);
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).startsWith("custom_metric,\"series=clients,clients=32\",12345.00,0.00,0.00,12345.00,12345.00,12345.00,us"));
    }
}
