package org.ulpgc.tarantino.common.messaging;

import org.ulpgc.tarantino.common.model.BookTask;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * A handle on one queue of SPEC §4.2 that carries each {@link BookTask} as {@code TarantinoJson} text. A handle belongs
 * to one thread.
 */
public interface BookTasks {

    void offer(BookTask task);

    /** The next task, waiting up to {@code wait} for one. */
    Optional<BookTask> poll(Duration wait) throws InterruptedException;

    /** Up to {@code max} tasks that are already waiting, without blocking. */
    List<BookTask> drain(int max);

    /** Acknowledges what this handle received since the last call; only persistent ActiveMQ needs it (SPEC §11.2). */
    void acknowledge();
}
