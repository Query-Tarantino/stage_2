package org.ulpgc.tarantino.indexer.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.config.CrashPoint;
import org.ulpgc.tarantino.common.model.BatchPublished;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.operations.CrashPoints;
import org.ulpgc.tarantino.common.operations.Transition;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.indexer.model.batch.Batch;
import org.ulpgc.tarantino.indexer.model.book.ParsedBook;
import org.ulpgc.tarantino.indexer.model.terms.BatchPostings;
import org.ulpgc.tarantino.indexer.model.terms.TermPostings;
import org.ulpgc.tarantino.indexer.ports.BatchAnnouncer;
import org.ulpgc.tarantino.indexer.ports.BatchStore;
import org.ulpgc.tarantino.indexer.ports.IndexWriter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Gatherers;

/**
 * Indexes one batch (SPEC §5.1): opens it in the write generation, claims its books, writes their metadata and
 * postings and publishes it. Until it is published nothing it wrote is visible; if its lease is lost first, it marks
 * no book and announces nothing, and {@code control} abandons it. The postings go in {@code bulkWrite}s of
 * {@value #POSTINGS_PER_WRITE} terms, so a crash between two of them leaves the batch half-written. It stops at the
 * indexer's crash points of SPEC §8.
 */
public class IndexBatchCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexBatchCommand.class);
    private static final int POSTINGS_PER_WRITE = 1_000;

    private final BatchStore batches;
    private final BookReader reader;
    private final IndexWriter writer;
    private final BatchAnnouncer announcer;
    private final TransitionLog log;
    private final CrashPoints crashPoints;

    public IndexBatchCommand(BatchStore batches, BookReader reader, IndexWriter writer, BatchAnnouncer announcer, TransitionLog log,
                             CrashPoints crashPoints) {
        this.batches = batches;
        this.reader = reader;
        this.writer = writer;
        this.announcer = announcer;
        this.log = log;
        this.crashPoints = crashPoints;
    }

    /**
     * {@code books} maps each book of the batch to the {@code enqueued_at} of its message. Those messages are already
     * out of {@code books-to-index}, so the {@code after-dequeue} point comes first.
     */
    public void execute(Map<Integer, Instant> books) {
        crashPoints.hit(CrashPoint.INDEXER_AFTER_DEQUEUE);
        Batch batch = batches.open(batches.writeGeneration());
        log.write(Transition.batch(batch.batchId(), null, BatchStatus.WRITING, batch.generation()));
        List<Integer> claimed = batches.claim(batch, books.keySet());
        claimed.forEach(bookId -> log.write(Transition.book(bookId, BookStatus.DOWNLOADED, BookStatus.INDEXING)
                .inBatch(batch.batchId(), batch.generation()).fromMessageEnqueuedAt(books.get(bookId))));
        List<ParsedBook> parsed = new ArrayList<>();
        for (int bookId : claimed) {
            parse(batch, bookId).ifPresent(parsed::add);
        }
        if (parsed.isEmpty()) {
            LOGGER.info("Batch {} has no books to index; it is left to expire", batch.batchId());
            return;
        }
        write(batch, parsed);
        publish(batch, parsed.stream().map(book -> book.metadata().bookId()).toList());
    }

    private Optional<ParsedBook> parse(Batch batch, int bookId) {
        Optional<ParsedBook> book = reader.read(bookId);
        if (book.isEmpty()) {
            failAtIndexing(batch, bookId);
        }
        return book;
    }

    private void failAtIndexing(Batch batch, int bookId) {
        if (batches.failAtIndexing(batch, bookId)) {
            log.write(Transition.book(bookId, BookStatus.INDEXING, BookStatus.FAILED).inBatch(batch.batchId(), batch.generation()));
        }
    }

    private void write(Batch batch, List<ParsedBook> books) {
        writer.writeMetadata(batch.generation(), books.stream().map(ParsedBook::metadata).toList());
        List<List<TermPostings>> postingsChunks = BatchPostings.of(books.stream()
                        .collect(Collectors.toMap(book -> book.metadata().bookId(), ParsedBook::frequencies)))
                .stream()
                .gather(Gatherers.windowFixed(POSTINGS_PER_WRITE))
                .toList();
        for (List<TermPostings> chunk : postingsChunks) {
            writer.writePostings(batch, chunk);
            crashPoints.hit(CrashPoint.DURING_BATCH_WRITE);
        }
    }

    private void publish(Batch batch, List<Integer> bookIds) {
        Optional<Instant> publishedAt = batches.publish(batch, bookIds);
        if (publishedAt.isEmpty()) {
            LOGGER.warn("Batch {} lost its lease before publishing; control abandons it", batch.batchId());
            return;
        }
        log.write(Transition.batch(batch.batchId(), BatchStatus.WRITING, BatchStatus.PUBLISHED, batch.generation()));
        crashPoints.hit(CrashPoint.AFTER_PUBLISH);
        batches.markPublished(batch);
        bookIds.forEach(bookId -> log.write(Transition.book(bookId, BookStatus.INDEXING, BookStatus.PUBLISHED)
                .inBatch(batch.batchId(), batch.generation())));
        crashPoints.hit(CrashPoint.AFTER_MARK);
        announcer.announce(new BatchPublished(batch.batchId(), batch.generation(), bookIds.size(), publishedAt.get()));
    }
}
