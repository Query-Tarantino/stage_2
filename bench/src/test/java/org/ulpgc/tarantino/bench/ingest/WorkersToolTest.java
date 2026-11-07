package org.ulpgc.tarantino.bench.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ulpgc.tarantino.bench.ingest.WorkerTimesTest.line;

class WorkersToolTest {

    @TempDir
    Path runDirectory;

    @Test
    void writesEachTrialInFactorOrderAndSummarisesEachConfigurationOverItsRepetitions() throws IOException {
        writeTrial("replicas-1-workers-16-rep-1", 4);
        writeTrial("replicas-1-workers-4-rep-2", 12);
        writeTrial("replicas-1-workers-4-rep-1", 8);

        assertEquals(WorkersTool.DONE, WorkersTool.run(List.of(runDirectory.toString())));

        assertEquals(List.of(
                        "replicas,workers,repetition,books,work_ms,gap_ms",
                        "1,4,1,2,8.000,1.000",
                        "1,4,2,2,12.000,1.000",
                        "1,16,1,2,4.000,1.000"),
                Files.readAllLines(runDirectory.resolve(WorkersTool.FILE_NAME)));
        assertEquals(List.of(
                        "metric,configuration,mean,std_dev,ci_95,p50,p95,p99,unit",
                        "download_work,\"replicas=1,workers=4\",10.00,2.83,25.41,8.00,12.00,12.00,ms",
                        "download_gap,\"replicas=1,workers=4\",1.00,0.00,0.00,1.00,1.00,1.00,ms",
                        "download_work,\"replicas=1,workers=16\",4.00,0.00,0.00,4.00,4.00,4.00,ms",
                        "download_gap,\"replicas=1,workers=16\",1.00,0.00,0.00,1.00,1.00,1.00,ms"),
                Files.readAllLines(runDirectory.resolve(SummaryStats.FILE_NAME)));
    }

    @Test
    void failsOnARunWithoutTrials() {
        assertEquals(WorkersTool.FAILED, WorkersTool.run(List.of(runDirectory.toString())));
    }

    @Test
    void answersWrongUsageWithoutTheRunDirectory() {
        assertEquals(WorkersTool.WRONG_USAGE, WorkersTool.run(List.of()));
    }

    private void writeTrial(String trial, long workMillis) throws IOException {
        Path trialLogs = Files.createDirectories(runDirectory.resolve("logs").resolve(trial));
        Files.write(trialLogs.resolve("crawler.log"), List.of(
                line("a", "download-0", 1, "QUEUED", "DOWNLOADING", 0),
                line("a", "download-0", 1, "DOWNLOADING", "DOWNLOADED", workMillis),
                line("a", "download-0", 2, "QUEUED", "DOWNLOADING", workMillis + 1),
                line("a", "download-0", 2, "DOWNLOADING", "DOWNLOADED", 2 * workMillis + 1)));
        Files.write(trialLogs.resolve("control.log"), List.of(
                "control-1  | 2026-11-05T22:01:18Z [main] INFO org.ulpgc.tarantino.common.operations.TransitionLog - transition"
                        + " entity=book id=1 from=- to=QUEUED batch_id=- generation=- enqueued_at=- at=2026-11-05T22:01:18Z replica=c"));
    }
}
