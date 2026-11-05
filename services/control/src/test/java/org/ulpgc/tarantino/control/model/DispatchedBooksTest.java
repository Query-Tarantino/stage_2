package org.ulpgc.tarantino.control.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DispatchedBooksTest {

    private static final Instant ENQUEUED_AT = Instant.parse("2026-10-21T10:05:00Z");
    private static final Instant NOW = Instant.parse("2026-10-21T10:10:00Z");

    @Test
    void keepsOnlyTheBooksStillWaitingAndLeavesRoomInTheWindow() {
        DispatchedBooks books = new DispatchedBooks();
        books.dispatched(1342, ENQUEUED_AT, NOW);
        books.dispatched(84, ENQUEUED_AT, NOW);

        books.keepOnly(Set.of(84)::contains);

        assertEquals(Set.of(84), books.bookIds());
        assertEquals(1, books.room(2));
        assertEquals(0, books.room(1));
    }

    @Test
    void dispatchesAgainOnlyTheBooksDispatchedLongerAgoThanTheRequeue() {
        DispatchedBooks books = new DispatchedBooks();
        books.dispatched(1342, ENQUEUED_AT, NOW.minusSeconds(11));
        books.dispatched(84, ENQUEUED_AT, NOW.minusSeconds(9));

        assertEquals(List.of(1342), books.dueAgain(NOW, Duration.ofSeconds(10)).stream().map(DispatchedBooks.Dispatch::bookId).toList());
    }
}
