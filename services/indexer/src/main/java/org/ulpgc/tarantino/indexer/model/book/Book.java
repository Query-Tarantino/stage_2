package org.ulpgc.tarantino.indexer.model.book;

import java.nio.file.Path;

public record Book(int bookId, String title, String author, String language, Path path) {
}
