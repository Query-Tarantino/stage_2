package org.ulpgc.tarantino.indexer.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.indexer.ports.BatchStore;

import java.time.Duration;

/**
 * The batch loop of an indexer replica in mode {@code polling} (SPEC §11.3): while no book is {@code DOWNLOADED} it
 * sleeps {@code TARANTINO_POLL_MILLIS}; otherwise it fills one batch by polling. If another indexer takes the last books
 * first, the batch ends empty and is left to expire. If Mongo fails halfway, the batch stays unpublished and
 * {@code control} sends its books back.
 */
public class PollingIndexWorker implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(PollingIndexWorker.class);

    private final BatchStore batches;
    private final IndexBatchCommand index;
    private final int batchSize;
    private final Duration timeout;
    private final Duration poll;

    public PollingIndexWorker(BatchStore batches, IndexBatchCommand index, int batchSize, Duration timeout, Duration poll) {
        this.batches = batches;
        this.index = index;
        this.batchSize = batchSize;
        this.timeout = timeout;
        this.poll = poll;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                indexNextBatchOrSleep();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                LOGGER.warn("Dropped a batch", e);
                sleep();
            }
        }
    }

    private void indexNextBatchOrSleep() throws InterruptedException {
        if (!batches.anyDownloaded()) {
            Thread.sleep(poll);
            return;
        }
        index.executeClaimingNext(batchSize, timeout, poll);
    }

    private void sleep() {
        try {
            Thread.sleep(poll);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
