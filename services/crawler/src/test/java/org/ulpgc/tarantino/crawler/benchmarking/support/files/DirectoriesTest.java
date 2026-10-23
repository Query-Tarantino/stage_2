package org.ulpgc.tarantino.crawler.benchmarking.support.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectoriesTest {

    @TempDir
    Path root;

    @Test
    void measuresFilesDirectoriesAndBytesRoundedUpToWholeBlocks() throws IOException {
        long block = Files.getFileStore(root).getBlockSize();
        Files.createDirectories(root.resolve("a/b"));
        Files.write(root.resolve("empty.txt"), new byte[0]);
        Files.write(root.resolve("a/one.txt"), new byte[1]);
        Files.write(root.resolve("a/b/block.txt"), new byte[(int) block]);
        Files.write(root.resolve("a/b/more.txt"), new byte[(int) block + 1]);

        assertEquals(new Footprint(4, 2, 2 * block + 2, 4 * block), Directories.footprint(root));
    }

    @Test
    void measuresNothingForAMissingDirectory() {
        assertEquals(Footprint.NONE, Directories.footprint(root.resolve("missing")));
    }
}
