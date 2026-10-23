package org.ulpgc.tarantino.crawler.model.failure;

import org.ulpgc.tarantino.common.model.BookErrorCode;

import java.io.Serial;

public class DownloadException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final BookErrorCode reason;

    public DownloadException(BookErrorCode reason, String message) {
        super(message);
        this.reason = reason;
    }

    public BookErrorCode reason() {
        return reason;
    }
}
