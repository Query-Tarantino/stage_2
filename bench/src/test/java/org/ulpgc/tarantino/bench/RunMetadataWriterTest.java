package org.ulpgc.tarantino.bench;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.bench.run.RunMetadataWriter;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunMetadataWriterTest {

    @Test
    void shouldCalculateSha256Correctly(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("sample.txt");
        Files.writeString(testFile, "hello world\n");
        String hash = RunMetadataWriter.sha256(testFile);
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    @Test
    void shouldVerifyCorpusSuccessfully(@TempDir Path tempDir) throws IOException {
        Path bookFile = tempDir.resolve("book.txt");
        Files.writeString(bookFile, "Sample content for testing");
        String hash = RunMetadataWriter.sha256(bookFile);

        Path shaFile = tempDir.resolve("corpus.sha256");
        Files.writeString(shaFile, hash + "  " + bookFile.toAbsolutePath() + "\n");

        assertDoesNotThrow(() -> RunMetadataWriter.verifyCorpus(shaFile));
    }

    @Test
    void shouldFailOnCorpusMismatch(@TempDir Path tempDir) throws IOException {
        Path bookFile = tempDir.resolve("book.txt");
        Files.writeString(bookFile, "Initial content");

        Path shaFile = tempDir.resolve("corpus.sha256");
        Files.writeString(shaFile, "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef  " + bookFile.toAbsolutePath() + "\n");

        assertThrows(IllegalStateException.class, () -> RunMetadataWriter.verifyCorpus(shaFile));
    }

    @Test
    void shouldWriteManifestJsonWithAllRequiredFields(@TempDir Path tempDir) throws IOException {
        Path runDir = tempDir.resolve("run-test");
        RunMetadataWriter.writeManifest(
                runDir,
                "20261028-120000-search-latency-test",
                "search-latency",
                "./scripts/bench/search-latency.sh",
                "2026-10-28T12:00:00Z",
                "2026-10-28T12:05:00Z",
                "1 8 32 64",
                "8 32 128",
                "1 2 3"
        );

        Path manifestPath = runDir.resolve("manifest.json");
        assertTrue(Files.exists(manifestPath));
        JsonNode root = TarantinoJson.mapper().readTree(manifestPath.toFile());

        assertEquals("20261028-120000-search-latency-test", root.get("run_id").asText());
        assertEquals("search-latency", root.get("experiment").asText());
        assertEquals("./scripts/bench/search-latency.sh", root.get("command").asText());
        assertEquals("2026-10-28T12:00:00Z", root.get("started_at").asText());
        assertEquals("2026-10-28T12:05:00Z", root.get("finished_at").asText());
        assertTrue(root.has("commit"));
        assertTrue(root.has("clean_tree"));

        JsonNode host = root.get("host");
        assertTrue(host.has("cpu"));
        assertTrue(host.has("cores"));
        assertTrue(host.has("memory_mb"));
        assertTrue(host.has("disk_gb"));
        assertTrue(host.has("os"));

        JsonNode docker = root.get("docker");
        assertTrue(docker.has("version"));
        assertTrue(docker.has("resources"));
        JsonNode dockerResources = docker.get("resources");
        assertTrue(dockerResources.has("cpus") && dockerResources.get("cpus").asInt() > 0);
        assertTrue(dockerResources.has("memory_mb") && dockerResources.get("memory_mb").asLong() > 0);

        JsonNode jvm = root.get("jvm");
        assertTrue(jvm.has("version"));
        assertTrue(jvm.has("flags") && jvm.get("flags").isArray());

        assertTrue(root.has("variables"));

        JsonNode replicas = root.get("replicas");
        assertEquals(3, replicas.size());
        assertEquals(1, replicas.get(0).asInt());

        JsonNode threads = root.get("threads");
        assertEquals(3, threads.size());
        assertEquals(8, threads.get(0).asInt());

        JsonNode concurrency = root.get("concurrency");
        assertEquals(4, concurrency.size());
        assertEquals(1, concurrency.get(0).asInt());
    }

    @Test
    void shouldWriteCorpusJson(@TempDir Path tempDir) throws IOException {
        Path shaFile = tempDir.resolve("corpus.sha256");
        Files.writeString(shaFile, "1342 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\n84 fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210\n");

        Path runDir = tempDir.resolve("run-test");
        RunMetadataWriter.writeCorpusJson(runDir, shaFile, "", 1);

        Path corpusPath = runDir.resolve("corpus.json");
        assertTrue(Files.exists(corpusPath));
        JsonNode root = TarantinoJson.mapper().readTree(corpusPath.toFile());

        assertTrue(root.has("ids") && root.get("ids").isArray());
        assertEquals(2, root.get("ids").size());
        assertEquals(1342, root.get("ids").get(0).asInt());
        assertEquals(84, root.get("ids").get(1).asInt());
        assertEquals(2, root.get("books").asInt());
        assertEquals(0, root.get("terms").asInt());
        assertTrue(root.has("sha256"));
    }

    @Test
    void shouldParseIntegerSeriesCorrectly() {
        assertEquals(List.of(1, 8, 32), RunMetadataWriter.parseIntegerSeries("1 8 32", List.of()));
        assertEquals(List.of(2, 4), RunMetadataWriter.parseIntegerSeries("2, 4", List.of()));
        assertEquals(List.of(10), RunMetadataWriter.parseIntegerSeries("", List.of(10)));
    }

    @Test
    void shouldParseDockerResourcesFromOutput() {
        RunMetadataWriter.DockerResources resources = RunMetadataWriter.parseDockerResources("8,16777216000");
        assertEquals(8, resources.cpus());
        assertEquals(16000, resources.memoryMb());

        RunMetadataWriter.DockerResources fallbackOnEmpty = RunMetadataWriter.parseDockerResources("");
        assertTrue(fallbackOnEmpty.cpus() > 0);
        assertTrue(fallbackOnEmpty.memoryMb() > 0);

        RunMetadataWriter.DockerResources fallbackOnInvalid = RunMetadataWriter.parseDockerResources("invalid,data");
        assertTrue(fallbackOnInvalid.cpus() > 0);
        assertTrue(fallbackOnInvalid.memoryMb() > 0);
    }
}
