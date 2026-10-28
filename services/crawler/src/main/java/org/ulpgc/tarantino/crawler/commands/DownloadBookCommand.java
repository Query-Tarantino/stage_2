package org.ulpgc.tarantino.crawler.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.config.CrashPoint;
import org.ulpgc.tarantino.common.model.BookErrorCode;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.operations.Lease;
import org.ulpgc.tarantino.common.operations.Transition;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.crawler.ports.BookClaims;
import org.ulpgc.tarantino.crawler.ports.TaskQueue;

import java.time.Clock;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One message of {@code books-to-download} (SPEC §5.1): it claims the book, ingests it and, once the download is
 * finished, enqueues it in {@code books-to-index}. A failed download goes back to {@code books-to-download} only if
 * “Fail a download” leaves it {@code QUEUED}. A message whose claim fails is a duplicate or stale and is dropped.
 * While it holds the book, a heartbeat renews its lease with “Renew a book”. It passes the crawler's crash points of
 * SPEC §8 on the way.
 */
public class DownloadBookCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadBookCommand.class);

    private final BookClaims claims;
    private final IngestBookCommand ingest;
    private final TaskQueue downloadQueue;
    private final TaskQueue indexQueue;
    private final TransitionLog log;
    private final Clock clock;
    private final Consumer<CrashPoint> crashPoints;
    private final Function<BooleanSupplier, Lease> heartbeats;

    public DownloadBookCommand(BookClaims claims, IngestBookCommand ingest, TaskQueue downloadQueue, TaskQueue indexQueue,
                               TransitionLog log, Clock clock, Consumer<CrashPoint> crashPoints,
                               Function<BooleanSupplier, Lease> heartbeats) {
        this.claims = claims;
        this.ingest = ingest;
        this.downloadQueue = downloadQueue;
        this.indexQueue = indexQueue;
        this.log = log;
        this.clock = clock;
        this.crashPoints = crashPoints;
        this.heartbeats = heartbeats;
    }

    public void execute(BookTask task) {
        Optional<String> claim = claims.claim(task.bookId());
        if (claim.isEmpty()) {
            return;
        }
        log.write(Transition.book(task.bookId(), BookStatus.QUEUED, BookStatus.DOWNLOADING).fromMessageEnqueuedAt(task.enqueuedAt()));
        crashPoints.accept(CrashPoint.AFTER_CLAIM);
        try (Lease lease = heartbeats.apply(() -> claims.renew(task.bookId(), claim.get()))) {
            download(task.bookId(), claim.get());
        }
    }

    private void download(int bookId, String claimId) {
        IngestResult result = ingest.execute(bookId);
        if (!result.succeeded()) {
            fail(bookId, claimId, result.failure());
            return;
        }
        crashPoints.accept(CrashPoint.AFTER_DATALAKE_WRITE);
        finish(bookId, claimId);
    }

    private void finish(int bookId, String claimId) {
        if (!claims.finish(bookId, claimId)) {
            return;
        }
        log.write(Transition.book(bookId, BookStatus.DOWNLOADING, BookStatus.DOWNLOADED));
        crashPoints.accept(CrashPoint.AFTER_DOWNLOADED);
        indexQueue.enqueue(new BookTask(bookId, clock.instant()));
    }

    private void fail(int bookId, String claimId, BookErrorCode reason) {
        Optional<BookStatus> outcome = claims.fail(bookId, claimId, reason);
        if (outcome.isEmpty()) {
            return;
        }
        log.write(Transition.book(bookId, BookStatus.DOWNLOADING, outcome.get()));
        LOGGER.warn("Book {} failed with {} and is {}", bookId, reason, outcome.get());
        if (outcome.get() == BookStatus.QUEUED) {
            downloadQueue.enqueue(new BookTask(bookId, clock.instant()));
        }
    }
}
