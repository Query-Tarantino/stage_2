package org.ulpgc.tarantino.crawler.commands;

import org.ulpgc.tarantino.common.model.BookErrorCode;
import org.ulpgc.tarantino.crawler.model.book.BookText;

/** A downloaded book's text, or the {@code error_code} why it could not be downloaded (SPEC §4.5). */
public record DownloadResult(int bookId, BookText text, BookErrorCode failure) {

    public static DownloadResult success(BookText text) {
        return new DownloadResult(text.bookId(), text, null);
    }

    public static DownloadResult failure(int bookId, BookErrorCode reason) {
        return new DownloadResult(bookId, null, reason);
    }

    public boolean succeeded() {
        return failure == null;
    }
}
