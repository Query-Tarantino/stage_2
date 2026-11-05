package org.ulpgc.tarantino.bench.recovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryDelayToolTest {

    private static final String FAILED_AT = "2026-11-03T09:19:18Z";

    @TempDir
    Path runs;

    @Test
    void takesEachRecoveryRunAsOneRepetitionOfItsModeAndReplicas() throws IOException {
        Path row04 = recoveryRun("20261103-091919-recovery-04-r1-05e4b5a", List.of(
                line("control", 1342, "QUEUED", "QUEUED", "2026-11-03T09:19:28Z"),
                line("crawler", 1342, "QUEUED", "DOWNLOADING", "2026-11-03T09:19:29Z")));
        Path row05 = recoveryRun("20261103-091959-recovery-05-r1-05e4b5a", List.of(
                line("control", 84, "DOWNLOADED", "DOWNLOADED", "2026-11-03T09:19:30Z"),
                line("indexer", 84, "DOWNLOADED", "INDEXING", "2026-11-03T09:19:31Z"),
                line("control", 11, "DOWNLOADED", "DOWNLOADED", "2026-11-03T09:19:32Z"),
                line("indexer", 11, "DOWNLOADED", "INDEXING", "2026-11-03T09:19:33Z")));
        Path output = Files.createDirectories(runs.resolve("20261105-120000-recovery-delay-abc1234"));

        assertEquals(0, RecoveryDelayTool.run(List.of(output.toString(), row04.toString(), row05.toString())));

        String summary = Files.readString(output.resolve("summary.csv"));
        assertTrue(summary.contains("requeue_delay,\"mode=hazelcast,replicas=1\",11500.00,2121.32,19059.31,12000.00,14000.00,14000.00,ms"), summary);
        assertTrue(summary.contains("reclaim_delay,\"mode=hazelcast,replicas=1\",12500.00,2121.32,19059.31,13000.00,15000.00,15000.00,ms"), summary);
    }

    @Test
    void leavesOutTheRequeueDelayWhenTheBrokerRedeliveredEveryBook() throws IOException {
        Path redelivered = recoveryRun("20261110-153000-recovery-05-activemq-persistent-r1-3f2a9c1", List.of(
                line("indexer", 84, "DOWNLOADED", "INDEXING", "2026-11-03T09:19:21Z")));
        Path output = Files.createDirectories(runs.resolve("20261110-160000-recovery-delay-3f2a9c1"));

        assertEquals(0, RecoveryDelayTool.run(List.of(output.toString(), redelivered.toString())));

        String summary = Files.readString(output.resolve("summary.csv"));
        assertFalse(summary.contains("requeue_delay"), summary);
        assertTrue(summary.contains("reclaim_delay,\"mode=activemq-persistent,replicas=1\",3000.00"), summary);
    }

    private Path recoveryRun(String runId, List<String> logLines) throws IOException {
        Path run = Files.createDirectories(runs.resolve(runId));
        Files.writeString(run.resolve(RecoveryChecks.FILE_NAME), "{\"failed_at\": \"" + FAILED_AT + "\"}");
        Path logs = Files.createDirectories(run.resolve("logs"));
        Files.write(logs.resolve("services.log"), logLines);
        return run;
    }

    private static String line(String service, int bookId, String from, String to, String at) {
        return service + "-1  | " + at + " [main] INFO TransitionLog - transition entity=book id=" + bookId + " from=" + from + " to=" + to
                + " batch_id=- generation=- enqueued_at=- at=" + at + " replica=" + service;
    }
}
