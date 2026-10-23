package org.ulpgc.tarantino.query.adapters.index.folders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FolderPerTermIndexReaderTest {

    @TempDir
    Path root;

    @Test
    void readsPostingsOfATermAndNothingForUnknownTerms() throws IOException {
        Files.createDirectories(root.resolve("i"));
        Files.writeString(root.resolve("i/island.txt"), "5\n1342\n");

        FolderPerTermIndexReader index = new FolderPerTermIndexReader(root);

        assertEquals(Set.of(5, 1342), index.postings("island"));
        assertEquals(Set.of(), index.postings("whale"));
    }

    @Test
    void readsNonAsciiTermsFromTheirEncodedFileName() throws IOException {
        Files.createDirectories(root.resolve("%C3%A9"));
        Files.writeString(root.resolve("%C3%A9/%C3%A9cume.txt"), "1342\n");

        assertEquals(Set.of(1342), new FolderPerTermIndexReader(root).postings("écume"));
    }
}
