package org.ulpgc.tarantino.indexer.adapters.local;

import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.indexer.ports.IndexQueue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The local index queue of a replica in mode {@code orchestrator} (SPEC §11.4), of {@code TARANTINO_LOCAL_INDEX_CAPACITY}:
 * {@code POST /internal/index} and {@code POST /index/update} offer to it and the batch loop gathers from it. What it
 * holds is lost if the replica dies, and the orchestrator dispatches it again.
 */
public class LocalIndexQueue implements IndexQueue {

    private final BlockingQueue<BookTask> tasks;

    public LocalIndexQueue(int capacity) {
        this.tasks = new ArrayBlockingQueue<>(capacity);
    }

    /** False when the queue is full. */
    public boolean offer(BookTask task) {
        return tasks.offer(task);
    }

    @Override
    public Optional<BookTask> next(Duration wait) throws InterruptedException {
        return Optional.ofNullable(tasks.poll(wait.toMillis(), TimeUnit.MILLISECONDS));
    }

    @Override
    public List<BookTask> drain(int max) {
        List<BookTask> drained = new ArrayList<>();
        tasks.drainTo(drained, max);
        return drained;
    }
}
