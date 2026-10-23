package org.ulpgc.tarantino.query.adapters.index.json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MonolithicJsonIndexReaderTest {

    @TempDir
    Path directory;

    @Test
    void readsPostingsFromTheIndexFile() throws IOException {
        Path file = Files.writeString(directory.resolve("inverted_index.json"), "{\"island\":[5,1342]}");

        MonolithicJsonIndexReader index = new MonolithicJsonIndexReader(file);

        assertEquals(Set.of(5, 1342), index.postings("island"));
        assertEquals(Set.of(), index.postings("whale"));
    }

    @Test
    void treatsAMissingIndexAsEmpty() {
        assertEquals(Set.of(), new MonolithicJsonIndexReader(directory.resolve("missing.json")).postings("island"));
    }
}
