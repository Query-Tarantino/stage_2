package org.ulpgc.tarantino.common.messaging;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.model.BookTask;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrokerTest {

    private static final BookTask TASK = new BookTask(1342, Instant.parse("2026-10-21T10:05:00Z"));

    @Test
    void offersOneTaskThroughAHandleItClosesRightAfter() {
        List<String> events = new ArrayList<>();

        new RecordingBroker(events).offer(MessagingContracts.BOOKS_TO_INDEX, TASK);

        assertEquals(List.of("open books-to-index", "offer 1342", "close"), events);
    }

    private record RecordingBroker(List<String> events) implements Broker {

        @Override
        public BookTasks queue(String name) {
            events.add("open " + name);
            return new RecordingHandle(events);
        }

        @Override
        public BatchAnnouncements announcements() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size(String queue) {
            return 0;
        }

        @Override
        public boolean connected() {
            return true;
        }

        @Override
        public String name() {
            return "recording";
        }

        @Override
        public void close() {
        }
    }

    private record RecordingHandle(List<String> events) implements BookTasks {

        @Override
        public void offer(BookTask task) {
            events.add("offer " + task.bookId());
        }

        @Override
        public Optional<BookTask> poll(Duration wait) {
            return Optional.empty();
        }

        @Override
        public List<BookTask> drain(int max) {
            return List.of();
        }

        @Override
        public void acknowledge() {
        }

        @Override
        public void close() {
            events.add("close");
        }
    }
}
