package org.ulpgc.tarantino.control.ports;

import org.ulpgc.tarantino.common.model.BookTask;

public interface DownloadQueue {

    void enqueue(BookTask task);
}
