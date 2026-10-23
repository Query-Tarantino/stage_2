package org.ulpgc.tarantino.common.operations;

import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.model.BookStatus;

import java.time.Instant;

/** A state change that a conditional write achieved (SPEC §4.8); null marks what does not apply. */
public record Transition(String entity, String id, String from, String to, String batchId, Integer generation,
                         Instant enqueuedAt) {

    /** {@code from} is null when the write creates the document. */
    public static Transition book(int bookId, BookStatus from, BookStatus to) {
        return new Transition("book", String.valueOf(bookId), nameOf(from), to.name(), null, null, null);
    }

    public static Transition batch(String batchId, BatchStatus from, BatchStatus to, int generation) {
        return new Transition("batch", batchId, nameOf(from), to.name(), null, generation, null);
    }

    public Transition inBatch(String batchId, int generation) {
        return new Transition(entity, id, from, to, batchId, generation, enqueuedAt);
    }

    /** The {@code enqueued_at} of the message the transition comes from, to measure the queue wait. */
    public Transition fromMessageEnqueuedAt(Instant instant) {
        return new Transition(entity, id, from, to, batchId, generation, instant);
    }

    private static String nameOf(Enum<?> status) {
        return status == null ? null : status.name();
    }
}
