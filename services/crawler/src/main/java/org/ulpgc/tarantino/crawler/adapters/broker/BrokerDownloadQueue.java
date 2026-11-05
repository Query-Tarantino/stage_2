package org.ulpgc.tarantino.crawler.adapters.broker;

import org.ulpgc.tarantino.common.messaging.BookTasks;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.crawler.ports.DownloadQueue;

import java.time.Duration;
import java.util.Optional;

/** The {@code books-to-download} queue of SPEC §4.2 through one worker's own handle. */
public class BrokerDownloadQueue implements DownloadQueue {

    private final BookTasks queue;

    public BrokerDownloadQueue(BookTasks queue) {
        this.queue = queue;
    }

    @Override
    public Optional<BookTask> next(Duration wait) throws InterruptedException {
        return queue.poll(wait);
    }

    @Override
    public void acknowledge() {
        queue.acknowledge();
    }
}
