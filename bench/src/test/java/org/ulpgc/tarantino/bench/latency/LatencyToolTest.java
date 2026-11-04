package org.ulpgc.tarantino.bench.latency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LatencyToolTest {

    private static final Instant START = Instant.parse("2026-11-04T14:00:00Z");

    @Test
    void summarisesEachConfigurationOverItsRepetitionsFromTheSuccessfulRequests(@TempDir Path runDirectory) throws IOException {
        writeTrial(runDirectory, "clients-8-threads-32-replicas-1-rep-1", List.of("0,200,50", "1000,200,50"));
        writeTrial(runDirectory, "clients-1-threads-32-replicas-1-rep-2", List.of("0,200,200", "1000,200,300", "2000,200,400"));
        writeTrial(runDirectory, "clients-1-threads-32-replicas-1-rep-1", List.of("0,200,100", "1000,200,200", "1500,500,9999", "2000,200,300"));
        writeTrial(runDirectory, "default", List.of("0,200,1"));

        assertEquals(LatencyTool.DONE, LatencyTool.run(List.of(runDirectory.toString())));

        assertEquals(List.of(
                        "metric,configuration,mean,std_dev,ci_95,p50,p95,p99,unit",
                        "search_latency_mean,\"clients=1,threads=32,replicas=1\",250.00,70.71,635.31,200.00,300.00,300.00,us",
                        "search_latency_p50,\"clients=1,threads=32,replicas=1\",250.00,70.71,635.31,200.00,300.00,300.00,us",
                        "search_latency_p95,\"clients=1,threads=32,replicas=1\",350.00,70.71,635.31,300.00,400.00,400.00,us",
                        "search_latency_p99,\"clients=1,threads=32,replicas=1\",350.00,70.71,635.31,300.00,400.00,400.00,us",
                        "search_throughput,\"clients=1,threads=32,replicas=1\",1.50,0.00,0.00,1.50,1.50,1.50,requests/s",
                        "search_latency_mean,\"clients=8,threads=32,replicas=1\",50.00,0.00,0.00,50.00,50.00,50.00,us",
                        "search_latency_p50,\"clients=8,threads=32,replicas=1\",50.00,0.00,0.00,50.00,50.00,50.00,us",
                        "search_latency_p95,\"clients=8,threads=32,replicas=1\",50.00,0.00,0.00,50.00,50.00,50.00,us",
                        "search_latency_p99,\"clients=8,threads=32,replicas=1\",50.00,0.00,0.00,50.00,50.00,50.00,us",
                        "search_throughput,\"clients=8,threads=32,replicas=1\",2.00,0.00,0.00,2.00,2.00,2.00,requests/s"),
                Files.readAllLines(runDirectory.resolve(SummaryStats.FILE_NAME)));
    }

    @Test
    void failsOnARunWithoutTrials(@TempDir Path runDirectory) {
        assertEquals(LatencyTool.FAILED, LatencyTool.run(List.of(runDirectory.toString())));
    }

    @Test
    void answersWrongUsageWithoutTheRunDirectory() {
        assertEquals(LatencyTool.WRONG_USAGE, LatencyTool.run(List.of()));
    }

    private static void writeTrial(Path runDirectory, String trial, List<String> millisStatusLatency) throws IOException {
        Path load = Files.createDirectories(runDirectory.resolve("load"));
        try (Writer rows = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(load.resolve(trial + ".csv.gz"))),
                StandardCharsets.UTF_8)) {
            rows.write("instant,path,status,latency_us\n");
            for (String request : millisStatusLatency) {
                String[] cells = request.split(",");
                rows.write(START.plusMillis(Long.parseLong(cells[0])) + ",/search?q=love," + cells[1] + "," + cells[2] + "\n");
            }
        }
    }
}
