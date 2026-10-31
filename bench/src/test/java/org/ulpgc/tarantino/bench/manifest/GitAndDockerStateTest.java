package org.ulpgc.tarantino.bench.manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitAndDockerStateTest {

    @Test
    void readsTheShortCommitAndACleanTreeWhenNothingChanged() {
        CommandRunner runner = answering(Map.of(GitState.SHORT_COMMIT, "3f2a9c1\n", GitState.CHANGED_FILES, ""));

        assertEquals(new GitState("3f2a9c1", true), GitState.of(runner));
    }

    @Test
    void readsADirtyTreeWhenAFileChanged() {
        CommandRunner runner = answering(Map.of(GitState.SHORT_COMMIT, "3f2a9c1\n", GitState.CHANGED_FILES, " M SPEC.md\n"));

        assertEquals(new GitState("3f2a9c1", false), GitState.of(runner));
    }

    @Test
    void leavesTheRunsOutOfTheChangedFilesButNotAnyOtherUntrackedFile(@TempDir Path repository) throws IOException {
        CommandRunner git = inRepository(repository);
        git.output(List.of("git", "init"));
        Path run = Files.createDirectories(repository.resolve("benchmarks/runs/20261031-134354-ingest-throughput-d403115"));
        Files.writeString(run.resolve("manifest.json"), "{}");

        boolean cleanWithARun = git.output(GitState.CHANGED_FILES).isBlank();
        Files.writeString(repository.resolve("Untracked.java"), "");

        assertTrue(cleanWithARun);
        assertFalse(git.output(GitState.CHANGED_FILES).isBlank());
    }

    @Test
    void readsTheVersionAndResourcesOfDocker() {
        String info = """
                {"ServerVersion":"28.3.2","OperatingSystem":"Docker Desktop","NCPU":8,"MemTotal":8222093312,"Containers":9}
                """;

        DockerState docker = DockerState.of(answering(Map.of(DockerState.INFO, info)));

        assertEquals(new DockerState("28.3.2", "Docker Desktop", 8, 8_222_093_312L), docker);
    }

    static CommandRunner answering(Map<List<String>, String> outputs) {
        return outputs::get;
    }

    private static CommandRunner inRepository(Path repository) {
        CommandRunner processes = new ProcessCommandRunner();
        return command -> processes.output(Stream.of(List.of(command.getFirst(), "-C", repository.toString()), command.subList(1, command.size()))
                .flatMap(List::stream).toList());
    }
}
