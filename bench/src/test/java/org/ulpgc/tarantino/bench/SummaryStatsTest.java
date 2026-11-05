package org.ulpgc.tarantino.bench;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

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
    void takesTheMeanAndCiOverTheRepetitionsAndThePercentilesOverThePooledValues() {
        SummaryStats.Metrics metrics = SummaryStats.overRepetitions(List.of(100.0, 300.0), List.of(50, 150, 250, 350, 1000));

        assertEquals(2, metrics.count());
        assertEquals(200.0, metrics.mean(), 0.001);
        assertEquals(Math.sqrt(20_000), metrics.stdDev(), 0.001);
        assertEquals(12.7062 * Math.sqrt(20_000) / Math.sqrt(2), metrics.ci95(), 0.01);
        assertEquals(250.0, metrics.p50());
        assertEquals(1000.0, metrics.p99());
    }

    @Test
    void averagesEachNonEmptyRepetitionAndPoolsTheirValues() {
        SummaryStats.Metrics metrics = SummaryStats.ofRepetitions(List.of(List.of(100, 100), List.of(), List.of(200, 400)));

        assertEquals(2, metrics.count());
        assertEquals(200.0, metrics.mean(), 0.001);
        assertEquals(100.0, metrics.p50());
        assertEquals(400.0, metrics.p95());
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
        Path trialGz = loadDir.resolve("trial.csv.gz");
        try (GZIPOutputStream gos = new GZIPOutputStream(Files.newOutputStream(trialGz))) {
            gos.write("instant,path,status,latency_us\n2026-10-27T12:00:00Z,/search,200,12345\n".getBytes(StandardCharsets.UTF_8));
        }

        SummaryStats.main(new String[]{tempDir.toString(), "series=clients,clients=32", trialGz.toString(), "custom_metric"});

        Path summaryCsv = tempDir.resolve("summary.csv");
        assertTrue(Files.exists(summaryCsv));
        List<String> lines = Files.readAllLines(summaryCsv);
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).startsWith("custom_metric,\"series=clients,clients=32\",12345.00,0.00,0.00,12345.00,12345.00,12345.00,us"));
    }

    @Test
    void shouldReadLatenciesFromConcatenatedGzip(@TempDir Path tempDir) throws IOException {
        ByteArrayOutputStream member1 = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(member1)) {
            gos.write("instant,path,status,latency_us\n2026-11-03T10:00:00Z,/search,200,1000\n2026-11-03T10:00:01Z,/search,503,9999\n"
                    .getBytes(StandardCharsets.UTF_8));
        }
        ByteArrayOutputStream member2 = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(member2)) {
            gos.write("instant,path,status,latency_us\n2026-11-03T10:00:02Z,/search,200,2000\n"
                    .getBytes(StandardCharsets.UTF_8));
        }

        Path gzPath = tempDir.resolve("pooled.csv.gz");
        try (OutputStream outputStream = Files.newOutputStream(gzPath)) {
            outputStream.write(member1.toByteArray());
            outputStream.write(member2.toByteArray());
        }

        List<Long> latencies = SummaryStats.readLatencies(gzPath);
        assertEquals(List.of(1000L, 2000L), latencies);
    }
}
