package org.ulpgc.tarantino.control.ports;

import org.ulpgc.tarantino.common.model.BookTask;

/** Where {@code control} requeues the downloaded books whose message was lost, {@code books-to-index} (SPEC §4.2 and §5.3, step 4). */
public interface IndexQueue {

    void enqueue(BookTask task);
}
