package org.ulpgc.tarantino.crawler.commands;

import org.ulpgc.tarantino.common.model.BookErrorCode;
import org.ulpgc.tarantino.crawler.model.book.StoredPaths;

public record IngestResult(int bookId, StoredPaths paths, BookErrorCode failure) {

    public static IngestResult success(int bookId, StoredPaths paths) {
        return new IngestResult(bookId, paths, null);
    }

    public static IngestResult failure(int bookId, BookErrorCode reason) {
        return new IngestResult(bookId, null, reason);
    }

    public boolean succeeded() {
        return failure == null;
    }
}
