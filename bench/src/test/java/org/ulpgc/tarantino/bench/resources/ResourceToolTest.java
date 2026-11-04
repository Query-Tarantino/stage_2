package org.ulpgc.tarantino.bench.resources;

import org.apache.commons.math3.distribution.TDistribution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceToolTest {

    private static final String TRANSITION = " INFO org.ulpgc.tarantino.common.operations.TransitionLog - transition ";

    @TempDir
    Path runDirectory;

    @Test
    void summarisesTheCpuAndMemoryOfEachServiceAndOfTheStackInEachPhaseOfEachConfiguration() throws IOException {
        Files.write(runDirectory.resolve("stats.csv"), List.of(
                "instant,container,cpu_percent,memory_bytes",
                "2026-10-21T10:00:01Z,stage_2-crawler-1,10.40,104857600",
                "2026-10-21T10:00:01.1Z,stage_2-crawler-2,20.20,209715200"));
        writeIngestionOfTrial("replicas-2-rep-1", "10");

        assertEquals(ResourceTool.DONE, ResourceTool.run(List.of(runDirectory.toString())));

        assertEquals(List.of(
                        "metric,configuration,mean,std_dev,ci_95,p50,p95,p99,unit",
                        "cpu_per_replica,\"replicas=2,phase=ingestion,service=crawler\",15.30,0.00,0.00,10.40,20.20,20.20,percent",
                        "cpu_total,\"replicas=2,phase=ingestion,service=crawler\",30.60,0.00,0.00,30.60,30.60,30.60,percent",
                        "memory_per_replica,\"replicas=2,phase=ingestion,service=crawler\",150.00,0.00,0.00,100.00,200.00,200.00,MiB",
                        "stack_cpu_total,\"replicas=2,phase=ingestion\",30.60,0.00,0.00,30.60,30.60,30.60,percent",
                        "stack_memory_total,\"replicas=2,phase=ingestion\",300.00,0.00,0.00,300.00,300.00,300.00,MiB"),
                Files.readAllLines(runDirectory.resolve(SummaryStats.FILE_NAME)));
    }

    @Test
    void takesTheCiOverTheMeanOfEachRepetitionAndThePercentilesOverAllTheirRefreshes() throws IOException {
        Files.write(runDirectory.resolve("stats.csv"), List.of(
                "instant,container,cpu_percent,memory_bytes",
                "2026-10-21T10:00:01Z,stage_2-crawler-1,10.00,104857600",
                "2026-10-21T11:00:01Z,stage_2-crawler-1,30.00,104857600"));
        writeIngestionOfTrial("replicas-1-rep-1", "10");
        writeIngestionOfTrial("replicas-1-rep-2", "11");

        ResourceTool.run(List.of(runDirectory.toString()));

        String[] cpuPerReplica = Files.readAllLines(runDirectory.resolve(SummaryStats.FILE_NAME)).get(1).split(",");
        assertEquals("cpu_per_replica", cpuPerReplica[0]);
        assertEquals(20.0, Double.parseDouble(cpuPerReplica[4]), 0.01);
        assertEquals(new TDistribution(1).inverseCumulativeProbability(0.975) * Math.sqrt(200) / Math.sqrt(2),
                Double.parseDouble(cpuPerReplica[6]), 0.01);
        assertEquals(List.of("10.00", "30.00"), List.of(cpuPerReplica[7], cpuPerReplica[8]));
    }

    @Test
    void failsOnARunWithoutStatsOrTrials() {
        assertEquals(ResourceTool.FAILED, ResourceTool.run(List.of(runDirectory.toString())));
    }

    @Test
    void answersWrongUsageWithoutTheRunDirectory() {
        assertEquals(ResourceTool.WRONG_USAGE, ResourceTool.run(List.of()));
    }

    private void writeIngestionOfTrial(String trial, String hour) throws IOException {
        Path trialLogs = Files.createDirectories(runDirectory.resolve("logs").resolve(trial));
        Files.write(trialLogs.resolve("crawler.log"), List.of(
                "crawler-1  | 2026-10-21T" + hour + ":00:00Z" + TRANSITION + "entity=book id=84 from=QUEUED to=DOWNLOADING batch_id=- generation=- enqueued_at=2026-10-21T09:59:59Z at=2026-10-21T" + hour + ":00:00Z replica=a",
                "crawler-1  | 2026-10-21T" + hour + ":00:02Z" + TRANSITION + "entity=book id=84 from=DOWNLOADING to=DOWNLOADED batch_id=- generation=- enqueued_at=- at=2026-10-21T" + hour + ":00:02Z replica=a"));
    }
}
