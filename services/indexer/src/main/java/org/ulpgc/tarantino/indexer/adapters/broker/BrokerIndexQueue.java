package org.ulpgc.tarantino.indexer.adapters.broker;

import org.ulpgc.tarantino.common.messaging.BookTasks;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.indexer.ports.IndexQueue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class BrokerIndexQueue implements IndexQueue {

    private final BookTasks queue;

    public BrokerIndexQueue(BookTasks queue) {
        this.queue = queue;
    }

    @Override
    public Optional<BookTask> next(Duration wait) throws InterruptedException {
        return queue.poll(wait);
    }

    @Override
    public List<BookTask> drain(int max) {
        return queue.drain(max);
    }
}
