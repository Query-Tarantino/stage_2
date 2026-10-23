package org.ulpgc.tarantino.indexer.model.book;

import java.nio.file.Path;

public record BookText(int bookId, String header, String body, Path bodyPath) {
}
