package org.ulpgc.tarantino.crawler.ports;

import org.ulpgc.tarantino.common.model.BookTask;

import java.time.Duration;
import java.util.Optional;

public interface DownloadQueue {

    /** The next message of {@code books-to-download}, waiting up to {@code wait} for one. */
    Optional<BookTask> next(Duration wait) throws InterruptedException;
}
