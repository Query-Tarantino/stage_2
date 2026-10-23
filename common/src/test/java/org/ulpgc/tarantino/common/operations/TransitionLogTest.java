package org.ulpgc.tarantino.common.operations;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.model.BookStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransitionLogTest {

    private final TransitionLog log =
            new TransitionLog("3f2b8c1e9a4d", Clock.fixed(Instant.parse("2026-10-21T10:05:02.481Z"), ZoneOffset.UTC));

    @Test
    void writesTheClaimOfAMessageWithItsEnqueueInstant() {
        Transition claim = Transition.book(1342, BookStatus.QUEUED, BookStatus.DOWNLOADING)
                .fromMessageEnqueuedAt(Instant.parse("2026-10-21T10:05:00Z"));

        assertEquals("transition entity=book id=1342 from=QUEUED to=DOWNLOADING batch_id=- generation=- "
                + "enqueued_at=2026-10-21T10:05:00Z at=2026-10-21T10:05:02.481Z replica=3f2b8c1e9a4d", log.line(claim));
    }

    @Test
    void writesABookClaimedByABatch() {
        Transition claim = Transition.book(84, BookStatus.DOWNLOADED, BookStatus.INDEXING).inBatch("6716f0c2a1b2c3d4e5f60718", 2);

        assertEquals("transition entity=book id=84 from=DOWNLOADED to=INDEXING batch_id=6716f0c2a1b2c3d4e5f60718 generation=2 "
                + "enqueued_at=- at=2026-10-21T10:05:02.481Z replica=3f2b8c1e9a4d", log.line(claim));
    }

    @Test
    void writesACreatedBatchWithoutBatchId() {
        Transition opened = Transition.batch("6716f0c2a1b2c3d4e5f60718", null, BatchStatus.WRITING, 1);

        assertEquals("transition entity=batch id=6716f0c2a1b2c3d4e5f60718 from=- to=WRITING batch_id=- generation=1 "
                + "enqueued_at=- at=2026-10-21T10:05:02.481Z replica=3f2b8c1e9a4d", log.line(opened));
    }
}
