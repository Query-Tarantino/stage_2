package org.ulpgc.tarantino.indexer.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * The batch loop of an indexer replica. If Mongo or Hazelcast fail halfway, it leaves the batch unpublished and goes
 * on: its lease expires and {@code control} sends its books back to the queue.
 */
public class IndexWorker implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexWorker.class);
    private static final Duration PAUSE_AFTER_FAILURE = Duration.ofSeconds(1);

    private final BatchCollector collector;
    private final IndexBatchCommand index;

    public IndexWorker(BatchCollector collector, IndexBatchCommand index) {
        this.collector = collector;
        this.index = index;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                indexNextBatch();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                LOGGER.warn("Dropped a batch: {}", e.getMessage());
                pauseAfterFailure();
            }
        }
    }

    private void indexNextBatch() throws InterruptedException {
        Map<Integer, Instant> books = collector.next();
        if (!books.isEmpty()) {
            index.execute(books);
        }
    }

    private static void pauseAfterFailure() {
        try {
            Thread.sleep(PAUSE_AFTER_FAILURE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
