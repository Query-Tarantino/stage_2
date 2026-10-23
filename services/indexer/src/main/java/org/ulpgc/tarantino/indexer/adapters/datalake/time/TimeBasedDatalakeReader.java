package org.ulpgc.tarantino.indexer.adapters.datalake.time;

import org.ulpgc.tarantino.indexer.adapters.datalake.BookFiles;
import org.ulpgc.tarantino.indexer.model.book.BookText;
import org.ulpgc.tarantino.indexer.ports.sources.DatalakeReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

public class TimeBasedDatalakeReader implements DatalakeReader {

    private static final String HEADER_SUFFIX = ".header.txt";
    private static final String BODY_SUFFIX = ".body.txt";
    private static final int BOOK_FILE_DEPTH = 3;

    private final Path root;

    public TimeBasedDatalakeReader(Path root) {
        this.root = root;
    }

    @Override
    public Optional<BookText> bookText(int bookId) {
        return bodyFile(bookId).flatMap(body -> BookFiles.bookText(bookId, body.resolveSibling(bookId + HEADER_SUFFIX), body));
    }

    private Optional<Path> bodyFile(int bookId) {
        return Files.isDirectory(root) ? firstFileNamed(bookId + BODY_SUFFIX) : Optional.empty();
    }

    private Optional<Path> firstFileNamed(String name) {
        try (Stream<Path> files = Files.find(root, BOOK_FILE_DEPTH, (path, attributes) -> path.endsWith(name))) {
            return files.findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
