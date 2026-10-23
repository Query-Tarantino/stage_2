package org.ulpgc.tarantino.crawler.commands;

import org.ulpgc.tarantino.crawler.model.book.BookText;
import org.ulpgc.tarantino.crawler.model.failure.FailureReason;

public record DownloadResult(int bookId, BookText text, FailureReason failure) {

    public static DownloadResult success(BookText text) {
        return new DownloadResult(text.bookId(), text, null);
    }

    public static DownloadResult failure(int bookId, FailureReason reason) {
        return new DownloadResult(bookId, null, reason);
    }

    public boolean succeeded() {
        return failure == null;
    }
}
