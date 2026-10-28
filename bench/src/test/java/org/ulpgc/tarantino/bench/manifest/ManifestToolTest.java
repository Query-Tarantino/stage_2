package org.ulpgc.tarantino.bench.manifest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestToolTest {

    private static final String DOCKER_INFO = """
            {"ServerVersion":"28.3.2","OperatingSystem":"Docker Desktop","NCPU":8,"MemTotal":8222093312}
            """;
    private static final String CONTROL_CONTAINER = """
            [{"Config": {"Labels": {"com.docker.compose.service": "control"},
                         "Env": ["TARANTINO_RECONCILE_SECONDS=3", "JAVA_VERSION=jdk-25.0.1+8", "JAVA_TOOL_OPTIONS=-Xmx512m"]}}]
            """;

    @TempDir
    Path runsDirectory;

    private final ByteArrayOutputStream printed = new ByteArrayOutputStream();

    @Test
    void startsARunNamedAfterTheExperimentAndCommitAndFinishesIt() throws IOException {
        int started = ManifestTool.run(List.of("start", runsDirectory.toString(), "recovery-04-r2", "0",
                "--", "scripts/recovery/04-indexer-after-claim.sh", "2"), stack(), new PrintStream(printed, true, StandardCharsets.UTF_8));

        String runId = printed.toString(StandardCharsets.UTF_8).strip();
        Path runDirectory = runsDirectory.resolve(runId);
        Manifest manifest = Manifest.readFrom(runDirectory);
        assertEquals(ManifestTool.DONE, started);
        assertTrue(runId.matches("\\d{8}-\\d{6}-recovery-04-r2-3f2a9c1"), runId);
        assertEquals(new GitState("3f2a9c1", true), manifest.git());
        assertEquals(List.of("scripts/recovery/04-indexer-after-claim.sh", "2"), manifest.command());
        assertEquals(new DockerState("28.3.2", "Docker Desktop", 8, 8_222_093_312L), manifest.docker());
        assertEquals(Map.of("control", new ServiceConfiguration(1, "jdk-25.0.1+8", "-Xmx512m",
                Map.of("TARANTINO_RECONCILE_SECONDS", "3"))), manifest.services());
        assertNull(manifest.end());

        assertEquals(ManifestTool.DONE, ManifestTool.run(List.of("finish", runDirectory.toString()), stack(), System.out));

        Manifest finished = Manifest.readFrom(runDirectory);
        assertFalse(finished.end().isBefore(finished.start()));
        assertEquals(manifest.withEnd(finished.end()), finished);
    }

    @Test
    void writesSnakeCaseJsonWithUtcDates() throws IOException {
        ManifestTool.run(List.of("start", runsDirectory.toString(), "coordination-cost", "4", "--", "run.sh"), stack(),
                new PrintStream(printed, true, StandardCharsets.UTF_8));

        Path written = runsDirectory.resolve(printed.toString(StandardCharsets.UTF_8).strip()).resolve(Manifest.FILE_NAME);
        JsonNode manifest = TarantinoJson.mapper().readTree(Files.readString(written));
        assertEquals(4, manifest.get("client_concurrency").asInt());
        assertTrue(manifest.get("start").asText().endsWith("Z"));
        assertTrue(manifest.get("host").has("free_disk_bytes"));
        assertEquals("-Xmx512m", manifest.get("services").get("control").get("java_tool_options").asText());
    }

    @Test
    void answersWrongUsageWithoutTheCommandOrTheConcurrency() {
        assertEquals(ManifestTool.WRONG_USAGE, ManifestTool.run(List.of("start", runsDirectory.toString(), "x", "0"), stack(), System.out));
        assertEquals(ManifestTool.WRONG_USAGE, ManifestTool.run(List.of("start", runsDirectory.toString(), "x", "many", "--", "run.sh"), stack(), System.out));
        assertEquals(ManifestTool.WRONG_USAGE, ManifestTool.run(List.of("finish"), stack(), System.out));
    }

    private static CommandRunner stack() {
        ProcessCommandRunner host = new ProcessCommandRunner();
        Map<List<String>, String> outputs = Map.of(
                GitState.SHORT_COMMIT, "3f2a9c1\n",
                GitState.CHANGED_FILES, "",
                DockerState.INFO, DOCKER_INFO,
                ServiceConfiguration.CONTAINER_IDS, "c1\n",
                List.of("docker", "inspect", "c1"), CONTROL_CONTAINER);
        return command -> outputs.containsKey(command) ? outputs.get(command) : host.output(command);
    }
}
