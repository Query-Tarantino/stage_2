package org.ulpgc.tarantino.indexer.adapters.datalake.batch;

import org.ulpgc.tarantino.indexer.adapters.datalake.BookFiles;
import org.ulpgc.tarantino.indexer.model.book.BookText;
import org.ulpgc.tarantino.indexer.ports.sources.DatalakeReader;

import java.nio.file.Path;
import java.util.Optional;

public class BatchBasedDatalakeReader implements DatalakeReader {

    private static final int BATCH_SIZE = 1000;
    private static final String HEADER_SUFFIX = ".header.txt";
    private static final String BODY_SUFFIX = ".body.txt";

    private final Path root;

    public BatchBasedDatalakeReader(Path root) {
        this.root = root;
    }

    @Override
    public Optional<BookText> bookText(int bookId) {
        Path directory = root.resolve(String.valueOf(bookId / BATCH_SIZE));
        return BookFiles.bookText(bookId, directory.resolve(bookId + HEADER_SUFFIX), directory.resolve(bookId + BODY_SUFFIX));
    }
}
