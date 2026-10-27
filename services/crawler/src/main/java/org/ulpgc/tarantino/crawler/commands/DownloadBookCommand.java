package org.ulpgc.tarantino.crawler.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.config.CrashPoint;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.operations.Transition;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.crawler.ports.BookClaims;
import org.ulpgc.tarantino.crawler.ports.IndexQueue;

import java.time.Clock;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * One message of {@code books-to-download} (SPEC §5.1): it claims the book, ingests it and, once the download is
 * finished, enqueues it in {@code books-to-index}. A message whose claim fails is a duplicate or stale and is dropped.
 * It passes the crawler's crash points of SPEC §8 on the way.
 */
public class DownloadBookCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadBookCommand.class);

    private final BookClaims claims;
    private final IngestBookCommand ingest;
    private final IndexQueue indexQueue;
    private final TransitionLog log;
    private final Clock clock;
    private final Consumer<CrashPoint> crashPoints;

    public DownloadBookCommand(BookClaims claims, IngestBookCommand ingest, IndexQueue indexQueue, TransitionLog log, Clock clock,
                               Consumer<CrashPoint> crashPoints) {
        this.claims = claims;
        this.ingest = ingest;
        this.indexQueue = indexQueue;
        this.log = log;
        this.clock = clock;
        this.crashPoints = crashPoints;
    }

    public void execute(BookTask task) {
        Optional<String> claim = claims.claim(task.bookId());
        if (claim.isEmpty()) {
            return;
        }
        log.write(Transition.book(task.bookId(), BookStatus.QUEUED, BookStatus.DOWNLOADING).fromMessageEnqueuedAt(task.enqueuedAt()));
        crashPoints.accept(CrashPoint.AFTER_CLAIM);
        IngestResult result = ingest.execute(task.bookId());
        if (!result.succeeded()) {
            LOGGER.warn("Book {} failed with {}; it stays DOWNLOADING until its lease expires", task.bookId(), result.failure());
            return;
        }
        finish(task.bookId(), claim.get());
    }

    private void finish(int bookId, String claimId) {
        if (!claims.finish(bookId, claimId)) {
            return;
        }
        log.write(Transition.book(bookId, BookStatus.DOWNLOADING, BookStatus.DOWNLOADED));
        indexQueue.enqueue(new BookTask(bookId, clock.instant()));
    }
}
