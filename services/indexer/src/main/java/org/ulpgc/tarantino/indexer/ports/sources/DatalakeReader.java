package org.ulpgc.tarantino.indexer.ports.sources;

import org.ulpgc.tarantino.indexer.model.book.BookText;

import java.util.Optional;

/** The datalake as {@code indexer} reads it, which it never writes (SPEC §4.6). */
public interface DatalakeReader {

    Optional<BookText> bookText(int bookId);
}
