package org.ulpgc.tarantino.crawler.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.crawler.ports.DownloadQueue;

import java.time.Duration;

/**
 * A download worker: it takes the messages of {@code books-to-download} one by one. If Mongo or Hazelcast fail
 * halfway, it drops the message without marking anything and goes on: the lease expires and {@code control} redoes it.
 */
public class DownloadWorker implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadWorker.class);
    private static final Duration POLL_WAIT = Duration.ofSeconds(1);

    private final DownloadQueue queue;
    private final DownloadBookCommand download;

    public DownloadWorker(DownloadQueue queue, DownloadBookCommand download) {
        this.queue = queue;
        this.download = download;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                queue.next(POLL_WAIT).ifPresent(download::execute);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                LOGGER.warn("Dropped a download: {}", e.getMessage());
                pauseAfterFailure();
            }
        }
    }

    private static void pauseAfterFailure() {
        try {
            Thread.sleep(POLL_WAIT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
