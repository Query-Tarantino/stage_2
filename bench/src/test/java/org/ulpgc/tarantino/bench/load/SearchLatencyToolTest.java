package org.ulpgc.tarantino.bench.load;

import org.apache.commons.math3.distribution.TDistribution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchLatencyToolTest {

    private static final String HEADER = "instant,path,status,latency_us";

    @TempDir
    Path runDirectory;

    @Test
    void summarisesEachConfigurationOverItsRepetitionsWithThePercentilesOverEveryAnsweredRequest() throws IOException {
        writeTrial("clients-1-threads-8-rep-1", "10:00:00Z,/search?q=love,200,100", "10:00:00.5Z,/search?q=love,503,9999",
                "10:00:01Z,/search?q=night,200,300");
        writeTrial("clients-1-threads-8-rep-2", "11:00:00Z,/search?q=love,200,500", "11:00:01Z,/search?q=love,200,700",
                "11:00:02Z,/search?q=night,200,900");
        writeTrial("clients-8-threads-8-rep-1", "12:00:00Z,/search?q=love,200,1000");

        assertEquals(SearchLatencyTool.DONE, SearchLatencyTool.run(List.of(runDirectory.toString())));

        List<String> summary = Files.readAllLines(runDirectory.resolve(SummaryStats.FILE_NAME));
        assertEquals(1 + 2 * 5, summary.size());
        double ci = new TDistribution(1).inverseCumulativeProbability(0.975) * Math.sqrt(2 * Math.pow(250, 2)) / Math.sqrt(2);
        assertEquals(String.format(Locale.ROOT, "search_latency_mean,\"clients=1,threads=8\",450.00,353.55,%.2f,500.00,900.00,900.00,us", ci),
                summary.get(1));
        assertEquals(List.of("search_latency_p95", "600.00"), metricAndMean(summary.get(3)));
        assertEquals(List.of("search_throughput", "1.75"), metricAndMean(summary.get(5)));
        assertEquals("search_latency_mean,\"clients=8,threads=8\",1000.00,0.00,0.00,1000.00,1000.00,1000.00,us", summary.get(6));
    }

    @Test
    void failsOnARunWithoutTrialFiles() {
        assertEquals(SearchLatencyTool.FAILED, SearchLatencyTool.run(List.of(runDirectory.toString())));
    }

    @Test
    void answersWrongUsageWithoutTheRunDirectory() {
        assertEquals(SearchLatencyTool.WRONG_USAGE, SearchLatencyTool.run(List.of()));
    }

    private static List<String> metricAndMean(String row) {
        return List.of(row.substring(0, row.indexOf(',')), row.split("\",")[1].split(",")[0]);
    }

    private void writeTrial(String trial, String... rows) throws IOException {
        Path load = Files.createDirectories(runDirectory.resolve("load"));
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(load.resolve(trial + ".csv.gz")))) {
            String day = "2026-11-04T";
            StringBuilder csv = new StringBuilder(HEADER).append('\n');
            List.of(rows).forEach(row -> csv.append(day).append(row).append('\n'));
            out.write(csv.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
