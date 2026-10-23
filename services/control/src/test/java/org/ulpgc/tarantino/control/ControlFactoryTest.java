package org.ulpgc.tarantino.control;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControlFactoryTest {

    @TempDir
    Path root;

    @Test
    void takesTheCandidatesOfTheWorkloadFileGivenEvenWithAMirror() throws IOException {
        write("workload/book_ids.txt", "84\n\n 1342 \n");
        write("mirror/11/pg11.txt", "text");

        assertEquals(List.of(84, 1342), ControlFactory.candidates(config(root.resolve("mirror")), Optional.of("book_ids.txt")));
    }

    @Test
    void takesEveryBookOfTheMirrorInIdOrderWithoutAFile() throws IOException {
        write("mirror/1342/pg1342.txt", "text");
        write("mirror/84/pg84.txt", "text");

        assertEquals(List.of(84, 1342), ControlFactory.candidates(config(root.resolve("mirror")), Optional.empty()));
    }

    @Test
    void takesTheSampleWithoutAFileOrAMirror() throws IOException {
        write("workload/sample_ids.txt", "1342\n84\n");

        assertEquals(List.of(1342, 84), ControlFactory.candidates(config(null), Optional.empty()));
    }

    private ControlConfig config(Path mirror) {
        return new ControlConfig(root.resolve("workload"), mirror, 8, 100);
    }

    private void write(String file, String content) throws IOException {
        Files.createDirectories(root.resolve(file).getParent());
        Files.writeString(root.resolve(file), content);
    }
}
