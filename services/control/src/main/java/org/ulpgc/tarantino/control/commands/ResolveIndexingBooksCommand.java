package org.ulpgc.tarantino.control.commands;

import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.operations.Transition;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.control.model.GenerationBatch;
import org.ulpgc.tarantino.control.model.IndexingBook;
import org.ulpgc.tarantino.control.ports.IndexQueue;
import org.ulpgc.tarantino.control.ports.IndexingBooks;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Step 3 of the reconciliation (SPEC §5.3): resolves the {@code INDEXING} books by the state of their batch, which it
 * reads once per batch. A {@code WRITING} batch is left alone, the books of an {@code ABANDONED} or missing one are
 * released and enqueued, and those of a {@code PUBLISHED} one complete its publication.
 */
public class ResolveIndexingBooksCommand {

    private final IndexingBooks books;
    private final IndexQueue queue;
    private final TransitionLog log;
    private final Clock clock;

    public ResolveIndexingBooksCommand(IndexingBooks books, IndexQueue queue, TransitionLog log, Clock clock) {
        this.books = books;
        this.queue = queue;
        this.log = log;
        this.clock = clock;
    }

    public void execute() {
        books.indexing().stream()
                .collect(Collectors.groupingBy(IndexingBook::batch, LinkedHashMap::new, Collectors.toList()))
                .forEach(this::resolve);
    }

    private void resolve(GenerationBatch batch, List<IndexingBook> booksOfBatch) {
        switch (books.statusOf(batch).orElse(BatchStatus.ABANDONED)) {
            case ABANDONED -> booksOfBatch.forEach(this::release);
            case PUBLISHED -> booksOfBatch.forEach(this::complete);
            case WRITING -> {
            }
        }
    }

    private void release(IndexingBook book) {
        if (books.release(book)) {
            log.write(transition(book, BookStatus.DOWNLOADED));
            queue.enqueue(new BookTask(book.bookId(), clock.instant()));
        }
    }

    private void complete(IndexingBook book) {
        if (books.complete(book)) {
            log.write(transition(book, BookStatus.PUBLISHED));
        }
    }

    private static Transition transition(IndexingBook book, BookStatus to) {
        return Transition.book(book.bookId(), BookStatus.INDEXING, to)
                .inBatch(book.batch().batchId(), book.batch().generation());
    }
}
