package org.ulpgc.tarantino.indexer.ports.sources;

import org.ulpgc.tarantino.indexer.model.book.BookText;

import java.util.Optional;

public interface DatalakeReader {

    Optional<BookText> bookText(int bookId);
}
