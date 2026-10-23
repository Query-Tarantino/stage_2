package org.ulpgc.tarantino.crawler.ports;

import org.ulpgc.tarantino.crawler.model.book.BookText;
import org.ulpgc.tarantino.crawler.model.book.StoredPaths;

import java.util.Optional;

public interface DatalakeStorage {

    StoredPaths save(BookText book);

    Optional<StoredPaths> pathsOf(int bookId);
}
