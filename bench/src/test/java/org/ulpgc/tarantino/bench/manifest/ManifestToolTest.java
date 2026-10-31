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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestToolTest {

    private static final String DOCKER_INFO = """
            {"ServerVersion":"28.3.2","OperatingSystem":"Docker Desktop","NCPU":8,"MemTotal":8222093312}
            """;
    private static final String CONTROL_REPLICA = """
            {"Config": {"Labels": {"com.docker.compose.service": "control"},
                        "Env": ["TARANTINO_RECONCILE_SECONDS=3", "JAVA_VERSION=jdk-25.0.1+8", "JAVA_TOOL_OPTIONS=-Xmx512m"]}}""";

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
    void recordsEachTrialWithTheReplicasRunningWhenItStarts() {
        ManifestTool.run(List.of("start", runsDirectory.toString(), "coordination-cost", "0", "--", "run.sh"), stack(),
                new PrintStream(printed, true, StandardCharsets.UTF_8));
        String runDirectory = runsDirectory.resolve(printed.toString(StandardCharsets.UTF_8).strip()).toString();

        assertEquals(ManifestTool.DONE, ManifestTool.run(List.of("trial", runDirectory, "replicas-1-rep-1"), stack(), System.out));
        assertEquals(ManifestTool.DONE, ManifestTool.run(List.of("trial", runDirectory, "replicas-2-rep-1"), stack("c1\nc2\n"), System.out));

        List<Trial> trials = Manifest.readFrom(Path.of(runDirectory)).trials();
        assertEquals(List.of("replicas-1-rep-1", "replicas-2-rep-1"), trials.stream().map(Trial::name).toList());
        assertEquals(List.of(1, 2), trials.stream().map(trial -> trial.services().get("control").replicas()).toList());
    }

    @Test
    void answersWrongUsageWithoutTheCommandOrTheConcurrency() {
        assertEquals(ManifestTool.WRONG_USAGE, ManifestTool.run(List.of("start", runsDirectory.toString(), "x", "0"), stack(), System.out));
        assertEquals(ManifestTool.WRONG_USAGE, ManifestTool.run(List.of("start", runsDirectory.toString(), "x", "many", "--", "run.sh"), stack(), System.out));
        assertEquals(ManifestTool.WRONG_USAGE, ManifestTool.run(List.of("finish"), stack(), System.out));
    }

    @Test
    void answersWrongUsageForATrialNotNamedAfterItsFactorsAndRepetition() {
        String runDirectory = runsDirectory.toString();
        assertEquals(ManifestTool.WRONG_USAGE, ManifestTool.run(List.of("trial", runDirectory, "replicas-2"), stack(), System.out));
        assertEquals(ManifestTool.WRONG_USAGE, ManifestTool.run(List.of("trial", runDirectory, "../replicas-2-rep-1"), stack(), System.out));
        assertEquals(ManifestTool.WRONG_USAGE, ManifestTool.run(List.of("trial", runDirectory), stack(), System.out));
    }

    private static CommandRunner stack() {
        return stack("c1\n");
    }

    private static CommandRunner stack(String controlContainerIds) {
        ProcessCommandRunner host = new ProcessCommandRunner();
        List<String> inspect = Stream.concat(ServiceConfiguration.INSPECT.stream(), controlContainerIds.lines()).toList();
        String controlContainers = controlContainerIds.lines().map(id -> CONTROL_REPLICA).collect(Collectors.joining(",", "[", "]"));
        Map<List<String>, String> outputs = Map.of(
                GitState.SHORT_COMMIT, "3f2a9c1\n",
                GitState.CHANGED_FILES, "",
                DockerState.INFO, DOCKER_INFO,
                ServiceConfiguration.CONTAINER_IDS, controlContainerIds,
                inspect, controlContainers);
        return command -> outputs.containsKey(command) ? outputs.get(command) : host.output(command);
    }
}
