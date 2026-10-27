package org.ulpgc.tarantino.crawler.ports;

import org.ulpgc.tarantino.common.model.BookTask;

/** A queue of SPEC §4.2 the crawler sends {@link BookTask}s to. */
public interface TaskQueue {

    void enqueue(BookTask task);
}
