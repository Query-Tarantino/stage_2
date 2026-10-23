package org.ulpgc.tarantino.crawler.adapters.datalake.book;

import org.ulpgc.tarantino.crawler.adapters.datalake.BookFiles;
import org.ulpgc.tarantino.crawler.model.book.BookText;
import org.ulpgc.tarantino.crawler.model.book.StoredPaths;
import org.ulpgc.tarantino.crawler.ports.DatalakeStorage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class BookBasedDatalakeAdapter implements DatalakeStorage {

    private static final String HEADER_FILE = "header.txt";
    private static final String BODY_FILE = "body.txt";

    private final Path root;

    public BookBasedDatalakeAdapter(Path root) {
        this.root = root;
    }

    @Override
    public StoredPaths save(BookText book) {
        return BookFiles.write(paths(book.bookId()), book);
    }

    @Override
    public Optional<StoredPaths> pathsOf(int bookId) {
        return BookFiles.existing(paths(bookId));
    }

    @Override
    public int removeIncompleteWrites() {
        return BookFiles.removeIncompleteWrites(root);
    }

    @Override
    public Set<Integer> idsStoredSince(Instant instant) {
        return BookFiles.children(root).stream()
                .filter(directory -> BookFiles.modifiedSince(directory.resolve(BODY_FILE), instant))
                .map(directory -> Integer.valueOf(directory.getFileName().toString()))
                .collect(Collectors.toSet());
    }

    private StoredPaths paths(int bookId) {
        Path directory = root.resolve(String.valueOf(bookId));
        return new StoredPaths(directory.resolve(HEADER_FILE), directory.resolve(BODY_FILE));
    }
}
