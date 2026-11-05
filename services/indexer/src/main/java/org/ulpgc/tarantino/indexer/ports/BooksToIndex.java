package org.ulpgc.tarantino.indexer.ports;

import org.ulpgc.tarantino.common.model.BookTask;

/** Sends a book to the {@code books-to-index} queue (SPEC §4.2); a repeated message does no harm, because the claim decides. */
public interface BooksToIndex {

    void enqueue(BookTask task);
}
