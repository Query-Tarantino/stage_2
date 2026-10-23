package org.ulpgc.tarantino.crawler.ports;

import org.ulpgc.tarantino.common.model.BookTask;

public interface IndexQueue {

    void enqueue(BookTask task);
}
