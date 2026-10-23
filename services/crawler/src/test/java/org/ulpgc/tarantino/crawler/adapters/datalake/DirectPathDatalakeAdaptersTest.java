package org.ulpgc.tarantino.crawler.adapters.datalake;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.crawler.adapters.datalake.batch.BatchBasedDatalakeAdapter;
import org.ulpgc.tarantino.crawler.adapters.datalake.book.BookBasedDatalakeAdapter;
import org.ulpgc.tarantino.crawler.model.book.BookText;
import org.ulpgc.tarantino.crawler.model.book.StoredPaths;
import org.ulpgc.tarantino.crawler.ports.DatalakeStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectPathDatalakeAdaptersTest {

    @TempDir
    Path root;

    @Test
    void bookBasedLayoutUsesOneDirectoryPerBook() throws IOException {
        StoredPaths paths = new BookBasedDatalakeAdapter(root).save(new BookText(1342, "header", "body"));

        assertEquals(new StoredPaths(root.resolve("1342/header.txt"), root.resolve("1342/body.txt")), paths);
        assertEquals("body", Files.readString(paths.body()));
    }

    @Test
    void batchBasedLayoutGroupsBooksByThousands() {
        StoredPaths paths = new BatchBasedDatalakeAdapter(root).save(new BookText(64317, "header", "body"));

        assertEquals(new StoredPaths(root.resolve("64/64317.header.txt"), root.resolve("64/64317.body.txt")), paths);
    }

    @Test
    void findsPathsOfStoredBooksOnly() {
        DatalakeStorage datalake = new BatchBasedDatalakeAdapter(root);
        StoredPaths stored = datalake.save(new BookText(5, "header", "body"));

        assertEquals(Optional.of(stored), datalake.pathsOf(5));
        assertTrue(datalake.pathsOf(6).isEmpty());
    }
}
