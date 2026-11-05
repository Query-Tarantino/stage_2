package org.ulpgc.tarantino.crawler.ports;

import org.ulpgc.tarantino.common.model.BookState;

import java.util.Optional;

/** A book's state in {@code book_states}, as the API returns it (SPEC §6.2); empty if the book has no document. */
public interface BookStateReader {

    Optional<BookState> state(int bookId);
}
