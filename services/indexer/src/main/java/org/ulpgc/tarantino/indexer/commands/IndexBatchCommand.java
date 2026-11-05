package org.ulpgc.tarantino.indexer.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.config.CrashPoint;
import org.ulpgc.tarantino.common.model.BatchPublished;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.operations.CrashPoints;
import org.ulpgc.tarantino.common.operations.Lease;
import org.ulpgc.tarantino.common.operations.Transition;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.indexer.model.batch.Batch;
import org.ulpgc.tarantino.indexer.model.batch.ClaimedBook;
import org.ulpgc.tarantino.indexer.model.book.ParsedBook;
import org.ulpgc.tarantino.indexer.model.terms.BatchPostings;
import org.ulpgc.tarantino.indexer.model.terms.TermPostings;
import org.ulpgc.tarantino.indexer.ports.BatchAnnouncer;
import org.ulpgc.tarantino.indexer.ports.BatchStore;
import org.ulpgc.tarantino.indexer.ports.IndexWriter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Gatherers;

/**
 * Indexes one batch (SPEC §5.1): opens it in the write generation, claims its books, writes their metadata and
 * postings and publishes it. Until it is published nothing it wrote is visible; if its lease is lost first, it marks
 * no book and announces nothing, and {@code control} abandons it. The postings go in {@code bulkWrite}s of
 * {@value #POSTINGS_PER_WRITE} terms, so a crash between two of them leaves the batch half-written. It stops at the
 * indexer's crash points of SPEC §8. A heartbeat renews the batch's lease while it is open; once the lease is lost, it
 * writes nothing else and does not publish.
 */
public class IndexBatchCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexBatchCommand.class);
    /** Terms per postings {@code bulkWrite}, which the batch-writing benchmark also uses. */
    public static final int POSTINGS_PER_WRITE = 1_000;

    private final BatchStore batches;
    private final BookReader reader;
    private final IndexWriter writer;
    private final BatchAnnouncer announcer;
    private final TransitionLog log;
    private final CrashPoints crashPoints;
    private final Function<BooleanSupplier, Lease> heartbeats;

    /** {@code heartbeats} starts renewing a lease with its “Renew” write, which answers whether it matched. */
    public IndexBatchCommand(BatchStore batches, BookReader reader, IndexWriter writer, BatchAnnouncer announcer, TransitionLog log,
                             CrashPoints crashPoints, Function<BooleanSupplier, Lease> heartbeats) {
        this.batches = batches;
        this.reader = reader;
        this.writer = writer;
        this.announcer = announcer;
        this.log = log;
        this.crashPoints = crashPoints;
        this.heartbeats = heartbeats;
    }

    /**
     * {@code books} maps each book of the batch to the {@code enqueued_at} of its message. Those messages are already
     * out of {@code books-to-index}, so the {@code after-dequeue} point comes first.
     */
    public void execute(Map<Integer, Instant> books) {
        crashPoints.hit(CrashPoint.INDEXER_AFTER_DEQUEUE);
        Batch batch = batches.open(batches.writeGeneration());
        log.write(Transition.batch(batch.batchId(), null, BatchStatus.WRITING, batch.generation()));
        try (Lease lease = heartbeats.apply(() -> batches.renew(batch))) {
            index(batch, books, lease);
        }
    }

    /**
     * A batch of mode {@code polling} (SPEC §11.3): it opens the batch and claims the oldest {@code DOWNLOADED} book one
     * at a time, until it has {@code size} books or {@code timeout} has passed since its first claim, sleeping up to
     * {@code poll} while none waits. If no book is left to claim at first, the empty batch is left to expire.
     */
    public void executeClaimingNext(int size, Duration timeout, Duration poll) throws InterruptedException {
        Batch batch = batches.open(batches.writeGeneration());
        log.write(Transition.batch(batch.batchId(), null, BatchStatus.WRITING, batch.generation()));
        try (Lease lease = heartbeats.apply(() -> batches.renew(batch))) {
            Map<Integer, Instant> books = claimUntilFull(batch, size, timeout, poll);
            if (books.isEmpty()) {
                LOGGER.info("Batch {} found no book to claim; it is left to expire", batch.batchId());
                return;
            }
            indexClaimed(batch, books.keySet().stream().sorted().toList(), lease);
        }
    }

    private Map<Integer, Instant> claimUntilFull(Batch batch, int size, Duration timeout, Duration poll) throws InterruptedException {
        Map<Integer, Instant> books = new LinkedHashMap<>();
        Instant deadline = Instant.MAX;
        while (books.size() < size && Instant.now().isBefore(deadline)) {
            Optional<ClaimedBook> claimed = batches.claimNext(batch);
            if (claimed.isPresent()) {
                books.put(claimed.get().bookId(), claimed.get().enqueuedAt());
                logClaim(batch, claimed.get().bookId(), claimed.get().enqueuedAt());
                deadline = books.size() == 1 ? Instant.now().plus(timeout) : deadline;
            } else if (books.isEmpty()) {
                return books;
            } else {
                Thread.sleep(shorter(poll, Duration.between(Instant.now(), deadline)));
            }
        }
        return books;
    }

    private static Duration shorter(Duration poll, Duration remaining) {
        if (remaining.isNegative()) {
            return Duration.ZERO;
        }
        return remaining.compareTo(poll) < 0 ? remaining : poll;
    }

    private void index(Batch batch, Map<Integer, Instant> books, Lease lease) {
        List<Integer> claimed = batches.claim(batch, books.keySet());
        claimed.forEach(bookId -> logClaim(batch, bookId, books.get(bookId)));
        indexClaimed(batch, claimed, lease);
    }

    private void logClaim(Batch batch, int bookId, Instant enqueuedAt) {
        log.write(Transition.book(bookId, BookStatus.DOWNLOADED, BookStatus.INDEXING)
                .inBatch(batch.batchId(), batch.generation()).fromMessageEnqueuedAt(enqueuedAt));
    }

    private void indexClaimed(Batch batch, List<Integer> claimed, Lease lease) {
        List<ParsedBook> parsed = new ArrayList<>();
        for (int bookId : claimed) {
            parse(batch, bookId, lease).ifPresent(parsed::add);
        }
        if (parsed.isEmpty()) {
            LOGGER.info("Batch {} has no books to index; it is left to expire", batch.batchId());
            return;
        }
        boolean written = write(batch, parsed, lease);
        if (!written || lease.lost()) {
            LOGGER.warn("Batch {} lost its lease while writing; control abandons it", batch.batchId());
            return;
        }
        publish(batch, parsed.stream().map(book -> book.metadata().bookId()).toList());
    }

    private Optional<ParsedBook> parse(Batch batch, int bookId, Lease lease) {
        Optional<ParsedBook> book = reader.read(bookId);
        if (book.isEmpty()) {
            failAtIndexing(batch, bookId, lease);
        }
        return book;
    }

    /** Only while the batch keeps its lease: once it is lost, the job writes nothing else (SPEC §5.1). */
    private void failAtIndexing(Batch batch, int bookId, Lease lease) {
        if (!lease.lost() && batches.failAtIndexing(batch, bookId)) {
            log.write(Transition.book(bookId, BookStatus.INDEXING, BookStatus.FAILED).inBatch(batch.batchId(), batch.generation()));
        }
    }

    /** Writes the batch's metadata and postings; false as soon as its lease is lost before one of the writes. */
    private boolean write(Batch batch, List<ParsedBook> books, Lease lease) {
        if (lease.lost()) {
            return false;
        }
        writer.writeMetadata(batch.generation(), books.stream().map(ParsedBook::metadata).toList());
        List<List<TermPostings>> postingsChunks = BatchPostings.of(books.stream()
                        .collect(Collectors.toMap(book -> book.metadata().bookId(), ParsedBook::frequencies)))
                .stream()
                .gather(Gatherers.windowFixed(POSTINGS_PER_WRITE))
                .toList();
        for (List<TermPostings> chunk : postingsChunks) {
            if (lease.lost()) {
                return false;
            }
            writer.writePostings(batch, chunk);
            crashPoints.hit(CrashPoint.DURING_BATCH_WRITE);
        }
        return true;
    }

    private void publish(Batch batch, List<Integer> bookIds) {
        Optional<Instant> publishedAt = batches.publish(batch, bookIds);
        if (publishedAt.isEmpty()) {
            LOGGER.warn("Batch {} lost its lease before publishing; control abandons it", batch.batchId());
            return;
        }
        log.write(Transition.batch(batch.batchId(), BatchStatus.WRITING, BatchStatus.PUBLISHED, batch.generation()));
        crashPoints.hit(CrashPoint.AFTER_PUBLISH);
        markPublished(batch, bookIds);
        crashPoints.hit(CrashPoint.AFTER_MARK);
        announcer.announce(new BatchPublished(batch.batchId(), batch.generation(), bookIds.size(), publishedAt.get()));
    }

    /** Logs the {@code INDEXING}→{@code PUBLISHED} line only of the books whose own write matched (SPEC §4.8). */
    private void markPublished(Batch batch, List<Integer> bookIds) {
        for (int bookId : bookIds) {
            if (batches.markPublished(batch, bookId)) {
                log.write(Transition.book(bookId, BookStatus.INDEXING, BookStatus.PUBLISHED).inBatch(batch.batchId(), batch.generation()));
            }
        }
    }
}
