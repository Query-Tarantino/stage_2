package org.ulpgc.tarantino.indexer.adapters.datalake.batch;

import org.ulpgc.tarantino.common.datalake.DatalakePaths;
import org.ulpgc.tarantino.indexer.adapters.datalake.BookFiles;
import org.ulpgc.tarantino.indexer.model.book.BookText;
import org.ulpgc.tarantino.indexer.ports.sources.DatalakeReader;

import java.nio.file.Path;
import java.util.Optional;

public class BatchBasedDatalakeReader implements DatalakeReader {

    private final DatalakePaths paths;

    public BatchBasedDatalakeReader(Path root) {
        this.paths = new DatalakePaths(root);
    }

    @Override
    public Optional<BookText> bookText(int bookId) {
        return BookFiles.bookText(bookId, paths.header(bookId), paths.body(bookId));
    }
}
