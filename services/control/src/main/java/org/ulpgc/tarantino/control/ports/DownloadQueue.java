package org.ulpgc.tarantino.control.ports;

import org.ulpgc.tarantino.common.model.BookTask;

/** Where {@code control} enqueues the books it seeds or requeues, {@code books-to-download} (SPEC §4.2 and §5.3). */
public interface DownloadQueue {

    void enqueue(BookTask task);
}
