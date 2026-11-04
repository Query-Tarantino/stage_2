package org.ulpgc.tarantino.crawler.adapters.local;

import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.crawler.ports.DownloadQueue;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The local download queue of a replica in mode {@code orchestrator} (SPEC §11.4), of
 * {@code TARANTINO_LOCAL_DOWNLOAD_CAPACITY}: {@code POST /internal/download} offers to it and every download worker of
 * the replica takes from it. What it holds is lost if the replica dies, and the orchestrator dispatches it again.
 */
public class LocalDownloadQueue implements DownloadQueue {

    private final BlockingQueue<BookTask> tasks;

    public LocalDownloadQueue(int capacity) {
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
}
