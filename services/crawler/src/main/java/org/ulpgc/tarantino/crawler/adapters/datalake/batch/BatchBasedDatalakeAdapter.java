package org.ulpgc.tarantino.crawler.adapters.datalake.batch;

import org.ulpgc.tarantino.common.datalake.DatalakePaths;
import org.ulpgc.tarantino.crawler.model.book.BookText;
import org.ulpgc.tarantino.crawler.model.book.StoredPaths;
import org.ulpgc.tarantino.crawler.ports.DatalakeStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class BatchBasedDatalakeAdapter implements DatalakeStorage {

    private final DatalakePaths paths;

    public BatchBasedDatalakeAdapter(Path root) {
        this.paths = new DatalakePaths(root);
    }

    @Override
    public StoredPaths save(BookText book) {
        StoredPaths stored = paths(book.bookId());
        try {
            DatalakePaths.writeAtomically(stored.header(), book.header());
            DatalakePaths.writeAtomically(stored.body(), book.body());
            return stored;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<StoredPaths> pathsOf(int bookId) {
        return Optional.of(paths(bookId)).filter(stored -> Files.exists(stored.body()));
    }

    private StoredPaths paths(int bookId) {
        return new StoredPaths(paths.header(bookId), paths.body(bookId));
    }
}
