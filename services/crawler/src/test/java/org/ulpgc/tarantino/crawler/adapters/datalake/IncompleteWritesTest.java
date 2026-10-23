package org.ulpgc.tarantino.crawler.adapters.datalake;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ulpgc.tarantino.crawler.CrawlerConfig;
import org.ulpgc.tarantino.crawler.CrawlerFactory;
import org.ulpgc.tarantino.crawler.model.book.BookText;
import org.ulpgc.tarantino.crawler.model.book.StoredPaths;
import org.ulpgc.tarantino.crawler.ports.DatalakeStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncompleteWritesTest {

    @TempDir
    Path root;

    @ParameterizedTest
    @ValueSource(strings = {"time", "book", "batch"})
    void removesTemporaryFilesAndHeadersWithoutBodyButKeepsStoredBooks(String layout) throws IOException {
        DatalakeStorage datalake = CrawlerFactory.datalake(new CrawlerConfig(root, layout));
        StoredPaths stored = datalake.save(new BookText(1342, "header", "body"));
        StoredPaths interrupted = datalake.save(new BookText(2001, "header", "body"));
        Files.move(interrupted.body(), interrupted.body().resolveSibling(interrupted.body().getFileName() + ".tmp"));

        assertEquals(2, datalake.removeIncompleteWrites());
        assertEquals(List.of(stored.body(), stored.header()), remainingFiles());
        assertTrue(datalake.pathsOf(1342).isPresent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"time", "book", "batch"})
    void removesNothingFromAnEmptyOrMissingDatalake(String layout) {
        assertEquals(0, CrawlerFactory.datalake(new CrawlerConfig(root.resolve("missing"), layout)).removeIncompleteWrites());
    }

    @Test
    void removesTheDirectoriesLeftEmpty() throws IOException {
        DatalakeStorage datalake = CrawlerFactory.datalake(new CrawlerConfig(root, "book"));
        StoredPaths interrupted = datalake.save(new BookText(2001, "header", "body"));
        Files.delete(interrupted.body());

        datalake.removeIncompleteWrites();

        try (Stream<Path> entries = Files.list(root)) {
            assertEquals(0, entries.count());
        }
    }

    private List<Path> remainingFiles() throws IOException {
        try (Stream<Path> entries = Files.walk(root)) {
            return entries.filter(Files::isRegularFile).sorted().toList();
        }
    }
}
