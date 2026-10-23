package org.ulpgc.tarantino.crawler.adapters.datalake.batch;

import org.ulpgc.tarantino.crawler.adapters.datalake.BookFiles;
import org.ulpgc.tarantino.crawler.model.book.BookText;
import org.ulpgc.tarantino.crawler.model.book.StoredPaths;
import org.ulpgc.tarantino.crawler.ports.DatalakeStorage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class BatchBasedDatalakeAdapter implements DatalakeStorage {

    private static final int BATCH_SIZE = 1000;
    private static final String HEADER_SUFFIX = ".header.txt";
    private static final String BODY_SUFFIX = ".body.txt";

    private final Path root;

    public BatchBasedDatalakeAdapter(Path root) {
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
                .flatMap(batch -> BookFiles.children(batch).stream())
                .filter(file -> file.getFileName().toString().endsWith(BODY_SUFFIX) && BookFiles.modifiedSince(file, instant))
                .map(file -> BookFiles.bookId(file, BODY_SUFFIX))
                .collect(Collectors.toSet());
    }

    private StoredPaths paths(int bookId) {
        Path directory = root.resolve(String.valueOf(bookId / BATCH_SIZE));
        return new StoredPaths(directory.resolve(bookId + HEADER_SUFFIX), directory.resolve(bookId + BODY_SUFFIX));
    }
}
