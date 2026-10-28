package org.ulpgc.tarantino.control.commands;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.operations.Transition;
import org.ulpgc.tarantino.control.ports.LostMessages;
import org.ulpgc.tarantino.control.ports.QueueSizes;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequeueLostMessagesCommandTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:05:00Z");

    private final LostBooks messages = new LostBooks();
    private final List<BookTask> downloadQueue = new ArrayList<>();
    private final List<BookTask> indexQueue = new ArrayList<>();
    private final RecordingTransitionLog log = new RecordingTransitionLog();

    @BeforeEach
    void loseOneMessageOfEachQueueAndOneThatWasRequeuedFirst() {
        messages.lose(1342, BookStatus.QUEUED, true);
        messages.lose(84, BookStatus.QUEUED, false);
        messages.lose(11, BookStatus.DOWNLOADED, true);
    }

    @Test
    void requeuesQueuedBooksOnlyWhileTheDownloadQueueIsEmpty() {
        requeueWith(new Sizes(0, 2));

        assertEquals(List.of(new BookTask(1342, NOW)), downloadQueue);
        assertEquals(List.of(), indexQueue);
    }

    @Test
    void requeuesDownloadedBooksOnlyWhileTheIndexQueueIsEmpty() {
        requeueWith(new Sizes(5, 0));

        assertEquals(List.of(), downloadQueue);
        assertEquals(List.of(new BookTask(11, NOW)), indexQueue);
    }

    @Test
    void logsEachRequeueThatMatchedWithTheSameStateOnBothSides() {
        requeueWith(new Sizes(0, 0));

        assertEquals(List.of(Transition.book(1342, BookStatus.QUEUED, BookStatus.QUEUED),
                Transition.book(11, BookStatus.DOWNLOADED, BookStatus.DOWNLOADED)), log.transitions());
    }

    private void requeueWith(QueueSizes sizes) {
        new RequeueLostMessagesCommand(sizes, messages, downloadQueue::add, indexQueue::add, log,
                Clock.fixed(NOW, ZoneOffset.UTC)).execute();
    }

    private record Sizes(int downloads, int index) implements QueueSizes {
    }

    private static final class LostBooks implements LostMessages {

        private final Map<BookStatus, Map<Integer, Boolean>> outcomes = new EnumMap<>(BookStatus.class);

        void lose(int bookId, BookStatus status, boolean requeued) {
            outcomes.computeIfAbsent(status, ignored -> new LinkedHashMap<>()).put(bookId, requeued);
        }

        @Override
        public List<Integer> lost(BookStatus status) {
            return List.copyOf(outcomes.getOrDefault(status, Map.of()).keySet());
        }

        @Override
        public boolean requeue(int bookId, BookStatus status) {
            return outcomes.get(status).get(bookId);
        }
    }
}
