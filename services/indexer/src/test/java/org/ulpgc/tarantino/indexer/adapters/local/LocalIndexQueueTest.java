package org.ulpgc.tarantino.indexer.adapters.local;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.model.BookTask;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalIndexQueueTest {

    private static final Instant ENQUEUED_AT = Instant.parse("2026-10-21T10:05:00Z");

    @Test
    void refusesBooksBeyondItsCapacityAndGivesTheOthersInOrder() throws InterruptedException {
        LocalIndexQueue queue = new LocalIndexQueue(3);

        assertTrue(List.of(1342, 84, 11).stream().allMatch(bookId -> queue.offer(new BookTask(bookId, ENQUEUED_AT))));
        assertFalse(queue.offer(new BookTask(2600, ENQUEUED_AT)));

        assertEquals(1342, queue.next(Duration.ofMillis(10)).orElseThrow().bookId());
        assertEquals(List.of(84, 11), queue.drain(5).stream().map(BookTask::bookId).toList());
    }
}
