package org.ulpgc.tarantino.indexer.adapters.datalake.book;

import org.ulpgc.tarantino.indexer.adapters.datalake.BookFiles;
import org.ulpgc.tarantino.indexer.model.book.BookText;
import org.ulpgc.tarantino.indexer.ports.sources.DatalakeReader;

import java.nio.file.Path;
import java.util.Optional;

public class BookBasedDatalakeReader implements DatalakeReader {

    private static final String HEADER_FILE = "header.txt";
    private static final String BODY_FILE = "body.txt";

    private final Path root;

    public BookBasedDatalakeReader(Path root) {
        this.root = root;
    }

    @Override
    public Optional<BookText> bookText(int bookId) {
        Path directory = root.resolve(String.valueOf(bookId));
        return BookFiles.bookText(bookId, directory.resolve(HEADER_FILE), directory.resolve(BODY_FILE));
    }
}
