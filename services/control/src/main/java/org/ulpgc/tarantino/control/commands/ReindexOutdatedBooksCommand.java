package org.ulpgc.tarantino.control.commands;

import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.operations.Transition;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.control.ports.IndexQueue;
import org.ulpgc.tarantino.control.ports.OutdatedBooks;

import java.time.Clock;

/**
 * Step 6.1 of the reconciliation (SPEC §5.3): sends every {@code PUBLISHED} book of an older generation back to
 * {@code DOWNLOADED} and enqueues it, during a rebuild and also outside one.
 */
public class ReindexOutdatedBooksCommand {

    private final OutdatedBooks books;
    private final IndexQueue queue;
    private final TransitionLog log;
    private final Clock clock;

    public ReindexOutdatedBooksCommand(OutdatedBooks books, IndexQueue queue, TransitionLog log, Clock clock) {
        this.books = books;
        this.queue = queue;
        this.log = log;
        this.clock = clock;
    }

    public void execute() {
        int writeGeneration = books.writeGeneration();
        books.outdated(writeGeneration).stream()
                .filter(bookId -> books.reindex(bookId, writeGeneration))
                .forEach(this::reindexed);
    }

    private void reindexed(int bookId) {
        log.write(Transition.book(bookId, BookStatus.PUBLISHED, BookStatus.DOWNLOADED));
        queue.enqueue(new BookTask(bookId, clock.instant()));
    }
}
