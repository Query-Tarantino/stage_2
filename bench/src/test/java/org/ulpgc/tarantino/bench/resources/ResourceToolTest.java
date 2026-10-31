package org.ulpgc.tarantino.bench.resources;

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
    void summarisesTheCpuAndMemoryOfEachServiceInEachPhaseOfEachConfiguration() throws IOException {
        Files.write(runDirectory.resolve("stats.csv"), List.of(
                "instant,container,cpu_percent,memory_bytes",
                "2026-10-21T10:00:01Z,stage_2-crawler-1,10.40,104857600",
                "2026-10-21T10:00:01.1Z,stage_2-crawler-2,20.20,209715200"));
        Path trialLogs = Files.createDirectories(runDirectory.resolve("logs").resolve("replicas-2-rep-1"));
        Files.write(trialLogs.resolve("crawler.log"), List.of(
                "crawler-1  | 2026-10-21T10:00:00Z" + TRANSITION + "entity=book id=84 from=QUEUED to=DOWNLOADING batch_id=- generation=- enqueued_at=2026-10-21T09:59:59Z at=2026-10-21T10:00:00Z replica=a",
                "crawler-1  | 2026-10-21T10:00:02Z" + TRANSITION + "entity=book id=84 from=DOWNLOADING to=DOWNLOADED batch_id=- generation=- enqueued_at=- at=2026-10-21T10:00:02Z replica=a"));

        assertEquals(ResourceTool.DONE, ResourceTool.run(List.of(runDirectory.toString())));

        assertEquals(List.of(
                        "metric,configuration,mean,std_dev,ci_95,p50,p95,p99,unit",
                        "cpu_per_replica,\"replicas=2,phase=ingestion,service=crawler\",15.00,7.07,9.80,10,20,20,percent",
                        "cpu_total,\"replicas=2,phase=ingestion,service=crawler\",31.00,0.00,0.00,31,31,31,percent",
                        "memory_per_replica,\"replicas=2,phase=ingestion,service=crawler\",150.00,70.71,98.00,100,200,200,MiB"),
                Files.readAllLines(runDirectory.resolve(SummaryStats.FILE_NAME)));
    }

    @Test
    void failsOnARunWithoutStatsOrTrials() {
        assertEquals(ResourceTool.FAILED, ResourceTool.run(List.of(runDirectory.toString())));
    }

    @Test
    void answersWrongUsageWithoutTheRunDirectory() {
        assertEquals(ResourceTool.WRONG_USAGE, ResourceTool.run(List.of()));
    }
}
