package org.ulpgc.tarantino.query.ports;

import org.ulpgc.tarantino.query.model.BookMetadata;

import java.util.List;
import java.util.Optional;

public interface MetadataReader {

    Optional<BookMetadata> book(int bookId);

    List<BookMetadata> booksBy(String author);
}
