package org.ulpgc.tarantino.indexer.model.batch;

import java.time.Instant;

/** A book a batch claimed with “Claim the next book for a batch” (SPEC §5.1), with the {@code enqueued_at} it was waiting since. */
public record ClaimedBook(int bookId, Instant enqueuedAt) {
}
