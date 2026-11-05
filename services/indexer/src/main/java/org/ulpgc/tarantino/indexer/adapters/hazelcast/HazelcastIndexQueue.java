package org.ulpgc.tarantino.indexer.adapters.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import org.ulpgc.tarantino.common.hazelcast.BookTaskQueue;
import org.ulpgc.tarantino.common.hazelcast.HazelcastContracts;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.indexer.ports.IndexQueue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** The {@code books-to-index} queue of Hazelcast, from which a batch takes its messages (SPEC §4.2). */
public class HazelcastIndexQueue implements IndexQueue {

    private final BookTaskQueue queue;

    public HazelcastIndexQueue(HazelcastInstance client) {
        this.queue = new BookTaskQueue(client, HazelcastContracts.BOOKS_TO_INDEX);
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
