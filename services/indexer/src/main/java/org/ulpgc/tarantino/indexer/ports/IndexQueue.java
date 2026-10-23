package org.ulpgc.tarantino.indexer.ports;

import org.ulpgc.tarantino.common.model.BookTask;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** The {@code books-to-index} queue (SPEC §4.2). */
public interface IndexQueue {

    Optional<BookTask> next(Duration wait) throws InterruptedException;

    /** Up to {@code max} messages that are already waiting, without blocking. */
    List<BookTask> drain(int max);
}
