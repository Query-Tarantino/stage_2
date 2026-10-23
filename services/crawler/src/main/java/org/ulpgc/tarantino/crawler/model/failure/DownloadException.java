package org.ulpgc.tarantino.crawler.model.failure;

import java.io.Serial;

public class DownloadException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final FailureReason reason;

    public DownloadException(FailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public FailureReason reason() {
        return reason;
    }
}
