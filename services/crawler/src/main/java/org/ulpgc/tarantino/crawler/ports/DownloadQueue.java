package org.ulpgc.tarantino.crawler.ports;

import org.ulpgc.tarantino.common.model.BookTask;

import java.time.Duration;
import java.util.Optional;

/** The {@code books-to-download} queue a download worker takes its books from (SPEC §4.2). */
public interface DownloadQueue {

    /** The next message of {@code books-to-download}, waiting up to {@code wait} for one. */
    Optional<BookTask> next(Duration wait) throws InterruptedException;
}
