package org.ulpgc.tarantino.crawler.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.crawler.model.ClaimedBook;
import org.ulpgc.tarantino.crawler.ports.BookClaims;

import java.time.Duration;
import java.util.Optional;

/**
 * A download worker of mode {@code polling} (SPEC §11.3): it claims the oldest {@code QUEUED} book with “Claim the next
 * book” and asks again at once after each job, or sleeps {@code TARANTINO_POLL_MILLIS} when none waits. If Mongo fails
 * halfway, it drops the book without marking anything: the lease expires and {@code control} redoes it.
 */
public class PollingDownloadWorker implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(PollingDownloadWorker.class);

    private final BookClaims claims;
    private final DownloadBookCommand download;
    private final Duration poll;

    public PollingDownloadWorker(BookClaims claims, DownloadBookCommand download, Duration poll) {
        this.claims = claims;
        this.download = download;
        this.poll = poll;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                downloadNextOrSleep();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                LOGGER.warn("Dropped a download: {}", e.getMessage());
                sleep();
            }
        }
    }

    private void downloadNextOrSleep() throws InterruptedException {
        Optional<ClaimedBook> claimed = claims.claimNext();
        if (claimed.isEmpty()) {
            Thread.sleep(poll);
            return;
        }
        download.executeClaimed(claimed.get());
    }

    private void sleep() {
        try {
            Thread.sleep(poll);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
