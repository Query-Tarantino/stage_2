package org.ulpgc.tarantino.control;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControlFactoryTest {

    @TempDir
    Path root;

    @Test
    void takesTheSampleByDefaultEvenWithAMirror() throws IOException {
        write("workload/sample_ids.txt", "1342\n84\n");
        write("mirror/11/pg11.txt", "text");

        assertEquals(List.of(1342, 84), ControlFactory.candidates(config(Map.of())));
    }

    @Test
    void takesTheIdsOfTheCandidatesFileInItsOrder() throws IOException {
        write("workload/book_ids.txt", "84\n\n 1342 \n");

        assertEquals(List.of(84, 1342), ControlFactory.candidates(config(Map.of("TARANTINO_CANDIDATES", "book_ids.txt"))));
    }

    @Test
    void takesEveryBookOfTheMirrorInIdOrder() throws IOException {
        write("mirror/1342/pg1342.txt", "text");
        write("mirror/84/pg84.txt", "text");

        assertEquals(List.of(84, 1342), ControlFactory.candidates(config(Map.of("TARANTINO_CANDIDATES", "mirror"))));
    }

    private TarantinoConfig config(Map<String, String> variables) {
        Map<String, String> environment = new HashMap<>(variables);
        environment.put("TARANTINO_WORKLOAD", root.resolve("workload").toString());
        environment.put("TARANTINO_MIRROR", root.resolve("mirror").toString());
        return TarantinoConfig.from(environment, TarantinoService.CONTROL);
    }

    private void write(String file, String content) throws IOException {
        Files.createDirectories(root.resolve(file).getParent());
        Files.writeString(root.resolve(file), content);
    }
}
