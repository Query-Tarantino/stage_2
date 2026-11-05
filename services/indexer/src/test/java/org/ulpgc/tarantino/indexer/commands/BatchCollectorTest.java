package org.ulpgc.tarantino.indexer.commands;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.indexer.ports.IndexQueue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchCollectorTest {

    private static final Instant EARLY = Instant.parse("2026-10-21T10:05:00Z");
    private static final Instant LATE = Instant.parse("2026-10-21T10:07:00Z");

    private final InMemoryQueue queue = new InMemoryQueue();

    @Test
    void hasNoBatchWithoutMessages() throws InterruptedException {
        assertEquals(Map.of(), collector(10).next());
    }

    @Test
    void keepsEachBookOnceWithItsEarliestEnqueuedAt() throws InterruptedException {
        queue.add(new BookTask(1342, LATE), new BookTask(84, EARLY), new BookTask(1342, EARLY));

        assertEquals(Map.of(1342, EARLY, 84, EARLY), collector(10).next());
    }

    @Test
    void closesTheBatchOnceItHasItsSize() throws InterruptedException {
        queue.add(new BookTask(1342, EARLY), new BookTask(84, EARLY), new BookTask(11, EARLY));

        assertEquals(List.of(1342, 84), List.copyOf(collector(2).next().keySet()));
        assertEquals(List.of(11), List.copyOf(collector(2).next().keySet()));
    }

    private BatchCollector collector(int batchSize) {
        return new BatchCollector(queue, batchSize, Duration.ofMillis(50), Clock.systemUTC());
    }

    private static final class InMemoryQueue implements IndexQueue {

        private final Deque<BookTask> messages = new ArrayDeque<>();

        void add(BookTask... tasks) {
            messages.addAll(List.of(tasks));
        }

        @Override
        public Optional<BookTask> next(Duration wait) {
            return Optional.ofNullable(messages.poll());
        }

        @Override
        public List<BookTask> drain(int max) {
            List<BookTask> drained = new ArrayList<>();
            while (drained.size() < max && !messages.isEmpty()) {
                drained.add(messages.poll());
            }
            return drained;
        }
    }
}
