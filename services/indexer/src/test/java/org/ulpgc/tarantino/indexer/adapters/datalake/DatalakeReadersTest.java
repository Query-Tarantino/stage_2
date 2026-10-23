package org.ulpgc.tarantino.indexer.adapters.datalake;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.indexer.adapters.datalake.batch.BatchBasedDatalakeReader;
import org.ulpgc.tarantino.indexer.adapters.datalake.book.BookBasedDatalakeReader;
import org.ulpgc.tarantino.indexer.adapters.datalake.time.TimeBasedDatalakeReader;
import org.ulpgc.tarantino.indexer.model.book.BookText;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatalakeReadersTest {

    @TempDir
    Path root;

    @Test
    void readsEveryLayout() throws IOException {
        store("20250925/14/5.header.txt", "20250925/14/5.body.txt");
        store("1342/header.txt", "1342/body.txt");
        store("64/64317.header.txt", "64/64317.body.txt");

        assertEquals(book(5, "20250925/14/5.body.txt"), new TimeBasedDatalakeReader(root).bookText(5));
        assertEquals(book(1342, "1342/body.txt"), new BookBasedDatalakeReader(root).bookText(1342));
        assertEquals(book(64317, "64/64317.body.txt"), new BatchBasedDatalakeReader(root).bookText(64317));
    }

    @Test
    void ignoresBooksWithoutBody() throws IOException {
        Files.createDirectories(root.resolve("7"));
        Files.writeString(root.resolve("7/header.txt"), "header");

        assertTrue(new BookBasedDatalakeReader(root).bookText(7).isEmpty());
        assertTrue(new TimeBasedDatalakeReader(root.resolve("missing")).bookText(7).isEmpty());
    }

    private void store(String header, String body) throws IOException {
        Files.createDirectories(root.resolve(body).getParent());
        Files.writeString(root.resolve(header), "header");
        Files.writeString(root.resolve(body), "body");
    }

    private Optional<BookText> book(int bookId, String body) {
        return Optional.of(new BookText(bookId, "header", "body", root.resolve(body)));
    }
}
