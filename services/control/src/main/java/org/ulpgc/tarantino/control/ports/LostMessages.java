package org.ulpgc.tarantino.control.ports;

import org.ulpgc.tarantino.common.model.BookStatus;

import java.util.List;

public interface LostMessages {

    /**
     * The books in {@code status}, {@code QUEUED} or {@code DOWNLOADED}, whose {@code enqueued_at} is older than
     * {@code $$NOW − TARANTINO_REQUEUE_SECONDS} (SPEC §5.3, step 4).
     */
    List<Integer> lost(BookStatus status);

    /** “Requeue a lost message” (SPEC §5.1): whether it matched, false if someone moved or enqueued the book first. */
    boolean requeue(int bookId, BookStatus status);
}
