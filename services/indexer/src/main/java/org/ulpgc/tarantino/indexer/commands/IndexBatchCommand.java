package org.ulpgc.tarantino.indexer.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.model.BatchPublished;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.operations.Transition;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.indexer.model.batch.Batch;
import org.ulpgc.tarantino.indexer.model.book.BookText;
import org.ulpgc.tarantino.indexer.model.book.HeaderParser;
import org.ulpgc.tarantino.indexer.model.book.ParsedBook;
import org.ulpgc.tarantino.indexer.model.terms.BatchPostings;
import org.ulpgc.tarantino.indexer.ports.BatchAnnouncer;
import org.ulpgc.tarantino.indexer.ports.BatchStore;
import org.ulpgc.tarantino.indexer.ports.IndexWriter;
import org.ulpgc.tarantino.indexer.ports.sources.DatalakeReader;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Indexes one batch (SPEC §5.1): opens it in the write generation, claims its books, writes their metadata and
 * postings and publishes it. Until it is published nothing it wrote is visible; if its lease is lost first, it marks
 * no book and announces nothing, and {@code control} abandons it.
 */
public class IndexBatchCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexBatchCommand.class);

    private final BatchStore batches;
    private final DatalakeReader datalake;
    private final HeaderParser headerParser;
    private final Tokenizer tokenizer;
    private final IndexWriter writer;
    private final BatchAnnouncer announcer;
    private final TransitionLog log;

    public IndexBatchCommand(BatchStore batches, DatalakeReader datalake, HeaderParser headerParser, Tokenizer tokenizer,
                             IndexWriter writer, BatchAnnouncer announcer, TransitionLog log) {
        this.batches = batches;
        this.datalake = datalake;
        this.headerParser = headerParser;
        this.tokenizer = tokenizer;
        this.writer = writer;
        this.announcer = announcer;
        this.log = log;
    }

    /** {@code books} maps each book of the batch to the {@code enqueued_at} of its message. */
    public void execute(Map<Integer, Instant> books) {
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
        Optional<BookText> text = datalake.bookText(bookId);
        if (text.isEmpty()) {
            failAtIndexing(batch, bookId);
            return Optional.empty();
        }
        return text.map(book -> new ParsedBook(headerParser.metadata(book), tokenizer.frequencies(book.body())));
    }

    private void failAtIndexing(Batch batch, int bookId) {
        if (batches.failAtIndexing(batch, bookId)) {
            log.write(Transition.book(bookId, BookStatus.INDEXING, BookStatus.FAILED).inBatch(batch.batchId(), batch.generation()));
        }
    }

    private void write(Batch batch, List<ParsedBook> books) {
        writer.writeMetadata(batch.generation(), books.stream().map(ParsedBook::metadata).toList());
        writer.writePostings(batch, BatchPostings.of(books.stream()
                .collect(Collectors.toMap(book -> book.metadata().bookId(), ParsedBook::frequencies))));
    }

    private void publish(Batch batch, List<Integer> bookIds) {
        Optional<Instant> publishedAt = batches.publish(batch, bookIds);
        if (publishedAt.isEmpty()) {
            LOGGER.warn("Batch {} lost its lease before publishing; control abandons it", batch.batchId());
            return;
        }
        log.write(Transition.batch(batch.batchId(), BatchStatus.WRITING, BatchStatus.PUBLISHED, batch.generation()));
        batches.markPublished(batch);
        bookIds.forEach(bookId -> log.write(Transition.book(bookId, BookStatus.INDEXING, BookStatus.PUBLISHED)
                .inBatch(batch.batchId(), batch.generation())));
        announcer.announce(new BatchPublished(batch.batchId(), batch.generation(), bookIds.size(), publishedAt.get()));
    }
}
