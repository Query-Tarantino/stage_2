package org.ulpgc.tarantino.bench.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecoveryComparisonTest {

    private static final String R1 = "20261110-153000-recovery-04-r1-3f2a9c1";
    private static final String R2 = "20261110-154000-recovery-04-r2-3f2a9c1";
    private static final String R3 = "20261110-155000-recovery-04-r3-3f2a9c1";

    @TempDir
    Path runs;

    @Test
    void agreesWhenEveryRunEndsWithTheSameBooksAndSearchesWhateverItsTimes() throws IOException {
        run(R1, 20, 1342, 1.5);
        run(R2, 20, 1342, 9.25);
        run(R3, 20, 1342, 4.0);
        run("20261110-160000-recovery-05-r1-3f2a9c1", 19, 84, 2.0);
        Files.createDirectories(runs.resolve("20261110-161000-recovery-04-r2-3f2a9c1"));

        assertEquals(List.of(), new RecoveryComparison(runs).differences("04"));
    }

    @Test
    void namesTheRunsWhoseBooksOrSearchesDiffer() throws IOException {
        run(R1, 20, 1342, 1.5);
        run(R2, 19, 1342, 1.5);
        run(R3, 20, 84, 1.5);

        assertEquals(List.of(
                        R2 + " has other books than " + R1 + ": {\"FAILED\":0,\"PUBLISHED\":19}",
                        R3 + " answers /search?q=pride unlike " + R1),
                new RecoveryComparison(runs).differences("04"));
    }

    @Test
    void failsWithFewerThanTwoRuns() throws IOException {
        run(R1, 20, 1342, 1.5);

        assertEquals(List.of("Only 1 runs of script 04 have their checks.json"), new RecoveryComparison(runs).differences("04"));
    }

    private void run(String runId, int published, int foundBook, double recoverySeconds) throws IOException {
        Map<String, Integer> books = new LinkedHashMap<>();
        books.put("FAILED", 0);
        books.put("PUBLISHED", published);
        JsonNode status = TarantinoJson.mapper().valueToTree(Map.of("generation", 1, "books", books));
        Map<String, JsonNode> search = Map.of(
                "pride", TarantinoJson.mapper().valueToTree(Map.of("total", 1, "results", List.of(Map.of("book_id", foundBook)))),
                "love", TarantinoJson.mapper().valueToTree(Map.of("total", 0, "results", List.of())));
        Instant failedAt = Instant.parse("2026-11-10T15:30:00Z");
        new RecoveryChecks(status, search, failedAt, failedAt.plusMillis((long) (recoverySeconds * 1_000)), recoverySeconds)
                .writeTo(Files.createDirectories(runs.resolve(runId)));
    }
}
