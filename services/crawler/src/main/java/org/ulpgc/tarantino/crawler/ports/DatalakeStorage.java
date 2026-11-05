package org.ulpgc.tarantino.crawler.ports;

import org.ulpgc.tarantino.crawler.model.book.BookText;
import org.ulpgc.tarantino.crawler.model.book.StoredPaths;

import java.util.Optional;

/** The datalake as {@code crawler} writes it: one header and one body per book (SPEC §4.6). */
public interface DatalakeStorage {

    StoredPaths save(BookText book);

    Optional<StoredPaths> pathsOf(int bookId);
}
