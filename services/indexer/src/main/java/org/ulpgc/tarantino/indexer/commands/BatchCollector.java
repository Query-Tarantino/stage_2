package org.ulpgc.tarantino.indexer.commands;

import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.indexer.ports.IndexQueue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gathers the messages of a batch (SPEC §4.2): from the first one, until it has {@code TARANTINO_INDEX_BATCH} books or
 * {@code TARANTINO_BATCH_TIMEOUT_SECONDS} have passed. A book repeated in several messages keeps the earliest
 * {@code enqueued_at}, which its transition line carries (SPEC §4.8).
 */
public class BatchCollector {

    private static final Duration FIRST_MESSAGE_WAIT = Duration.ofSeconds(1);
    private static final Duration NEXT_MESSAGE_WAIT = Duration.ofMillis(200);

    private final IndexQueue queue;
    private final int batchSize;
    private final Duration timeout;
    private final Clock clock;

    public BatchCollector(IndexQueue queue, int batchSize, Duration timeout, Clock clock) {
        this.queue = queue;
        this.batchSize = batchSize;
        this.timeout = timeout;
        this.clock = clock;
    }

    /** The books of the next batch and the {@code enqueued_at} of each, in order of arrival; empty if no message came. */
    public Map<Integer, Instant> next() throws InterruptedException {
        Map<Integer, Instant> books = new LinkedHashMap<>();
        Optional<BookTask> first = queue.next(FIRST_MESSAGE_WAIT);
        if (first.isEmpty()) {
            return books;
        }
        add(books, first.get());
        Instant deadline = clock.instant().plus(timeout);
        while (books.size() < batchSize && clock.instant().isBefore(deadline)) {
            List<BookTask> drained = queue.drain(batchSize - books.size());
            drained.forEach(task -> add(books, task));
            if (drained.isEmpty()) {
                queue.next(NEXT_MESSAGE_WAIT).ifPresent(task -> add(books, task));
            }
        }
        return books;
    }

    private static void add(Map<Integer, Instant> books, BookTask task) {
        books.merge(task.bookId(), task.enqueuedAt(), (earlier, later) -> earlier.isBefore(later) ? earlier : later);
    }
}
