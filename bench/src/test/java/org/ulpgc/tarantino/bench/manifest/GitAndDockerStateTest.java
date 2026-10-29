package org.ulpgc.tarantino.bench.manifest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
