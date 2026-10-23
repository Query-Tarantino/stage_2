package org.ulpgc.tarantino.indexer.adapters.datalake.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.indexer.model.book.BookText;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchBasedDatalakeReaderTest {

    @TempDir
    Path root;

    @Test
    void readsABookFromItsThousandsDirectory() throws IOException {
        Files.createDirectories(root.resolve("64"));
        Files.writeString(root.resolve("64/64317.header.txt"), "header");
        Files.writeString(root.resolve("64/64317.body.txt"), "body");

        assertEquals(Optional.of(new BookText(64317, "header", "body")),
                new BatchBasedDatalakeReader(root).bookText(64317));
    }

    @Test
    void ignoresBooksWithoutBody() throws IOException {
        Files.createDirectories(root.resolve("0"));
        Files.writeString(root.resolve("0/7.header.txt"), "header");

        assertTrue(new BatchBasedDatalakeReader(root).bookText(7).isEmpty());
        assertTrue(new BatchBasedDatalakeReader(root.resolve("missing")).bookText(7).isEmpty());
    }
}
