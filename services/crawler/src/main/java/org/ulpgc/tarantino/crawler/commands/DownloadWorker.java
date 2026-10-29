package org.ulpgc.tarantino.crawler.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.config.CrashPoint;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.crawler.ports.DownloadQueue;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * A download worker: it takes the messages of {@code books-to-download} one by one. If Mongo or Hazelcast fail
 * halfway, it drops the message without marking anything and goes on: the lease expires and {@code control} redoes it.
 */
public class DownloadWorker implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadWorker.class);
    private static final Duration POLL_WAIT = Duration.ofSeconds(1);

    private final DownloadQueue queue;
    private final DownloadBookCommand download;
    private final Consumer<CrashPoint> crashPoints;

    public DownloadWorker(DownloadQueue queue, DownloadBookCommand download, Consumer<CrashPoint> crashPoints) {
        this.queue = queue;
        this.download = download;
        this.crashPoints = crashPoints;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                queue.next(POLL_WAIT).ifPresent(this::download);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                LOGGER.warn("Dropped a download: {}", e.getMessage());
                pauseAfterFailure();
            }
        }
    }

    private void download(BookTask task) {
        crashPoints.accept(CrashPoint.CRAWLER_AFTER_DEQUEUE);
        download.execute(task);
    }

    private static void pauseAfterFailure() {
        try {
            Thread.sleep(POLL_WAIT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
