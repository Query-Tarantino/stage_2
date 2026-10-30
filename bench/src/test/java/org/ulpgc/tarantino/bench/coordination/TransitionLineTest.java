package org.ulpgc.tarantino.bench.coordination;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransitionLineTest {

    @Test
    void readsTheKeysOfALineBehindTheComposeAndSlf4jPrefixes() {
        String logged = "crawler-2  | 2026-10-21T10:05:02.483918Z [download-1] INFO org.ulpgc.tarantino.common.operations.TransitionLog - "
                + "transition entity=book id=1342 from=QUEUED to=DOWNLOADING batch_id=- generation=- "
                + "enqueued_at=2026-10-21T10:05:00Z at=2026-10-21T10:05:02.481Z replica=3f2b8c1e9a4d";

        TransitionLine line = TransitionLine.parse(logged).orElseThrow();

        assertEquals(new TransitionLine("book", "1342", "QUEUED", "DOWNLOADING", null,
                Instant.parse("2026-10-21T10:05:00Z"), Instant.parse("2026-10-21T10:05:02.481Z")), line);
        assertTrue(line.isBook());
        assertTrue(line.moves("QUEUED", "DOWNLOADING"));
    }

    @Test
    void readsABatchLineAndTheCreationOfAState() {
        TransitionLine batch = TransitionLine.parse("transition entity=batch id=6717a0c2e4b0f81d2c9a5e31 from=WRITING to=PUBLISHED "
                + "batch_id=- generation=1 enqueued_at=- at=2026-10-21T10:05:09Z replica=a1").orElseThrow();
        TransitionLine created = TransitionLine.parse("transition entity=book id=84 from=- to=QUEUED "
                + "batch_id=- generation=- enqueued_at=- at=2026-10-21T10:04:58Z replica=c0").orElseThrow();

        assertTrue(batch.isBatch());
        assertEquals("6717a0c2e4b0f81d2c9a5e31", batch.id());
        assertNull(created.from());
        assertNull(created.enqueuedAt());
    }

    @Test
    void skipsEveryOtherLine() {
        assertEquals(Optional.empty(), TransitionLine.parse("control-1  | 2026-10-21T10:05:00Z INFO Reconciliation pass done"));
        assertEquals(Optional.empty(), TransitionLine.parse("indexer-1  | INFO org.ulpgc.tarantino.common.operations.TransitionLog started"));
    }
}
