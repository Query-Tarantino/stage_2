package org.ulpgc.tarantino.control.adapters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalMirrorTest {

    @TempDir
    Path mirror;

    @Test
    void listsTheBooksWithATextInIdOrder() throws IOException {
        for (String file : List.of("1342/pg1342.txt", "84/pg84.txt", "11/pg11-images.epub", "README", "cache/pgcache.txt")) {
            Files.createDirectories(mirror.resolve(file).getParent());
            Files.writeString(mirror.resolve(file), "text");
        }

        assertEquals(List.of(84, 1342), new LocalMirror(mirror).bookIds());
    }
}
