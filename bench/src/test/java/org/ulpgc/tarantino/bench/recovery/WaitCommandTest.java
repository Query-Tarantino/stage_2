package org.ulpgc.tarantino.bench.recovery;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaitCommandTest {

    private static final Instant START = Instant.parse("2026-10-21T10:00:00Z");

    private final MovingClock clock = new MovingClock(START);

    @Test
    void knowsTheBookStatesAndTheQueues() {
        assertEquals(Optional.of("books"), WaitCommand.sectionOf("DOWNLOADING"));
        assertEquals(Optional.of("queues"), WaitCommand.sectionOf("books-to-download"));
        assertEquals(Optional.empty(), WaitCommand.sectionOf("downloading"));
        assertEquals(Optional.empty(), WaitCommand.sectionOf("published-batches"));
    }

    @Test
    void returnsAsSoonAsAStateHasABookThroughAnswersThatFailed() {
        Deque<Object> answers = new ArrayDeque<>(List.of(
                status(0, 0), new UncheckedIOException(new IOException("refused")), new Answer(503, TarantinoJson.mapper().missingNode()),
                status(2, 0)));

        assertTrue(new WaitCommand(stack(answers), clock, clock.pause()).awaitAtLeast("DOWNLOADING", WaitCommand.ANY, WaitCommand.DEFAULT_TIMEOUT));
        assertEquals(START.plus(WaitCommand.POLL.multipliedBy(3)), clock.instant());
    }

    @Test
    void waitsForAQueueAndGivesUpAfterTheTimeout() {
        Deque<Object> answers = new ArrayDeque<>(List.of(status(5, 0)));

        assertFalse(new WaitCommand(stack(answers), clock, clock.pause()).awaitAtLeast("books-to-download", WaitCommand.ANY, WaitCommand.DEFAULT_TIMEOUT));
        assertEquals(START.plus(WaitCommand.DEFAULT_TIMEOUT), clock.instant());
    }

    @Test
    void waitsUntilAStateReachesTheMinimum() {
        Deque<Object> answers = new ArrayDeque<>(List.of(status(1500, 0), status(2799, 0), status(2800, 0)));

        assertTrue(new WaitCommand(stack(answers), clock, clock.pause()).awaitAtLeast("DOWNLOADING", 2800, WaitCommand.DEFAULT_TIMEOUT));
        assertEquals(START.plus(WaitCommand.POLL.multipliedBy(2)), clock.instant());
    }

    @Test
    void keepsWaitingPastTheDefaultTimeoutWhenAWholePhaseNeedsLonger() {
        Deque<Object> answers = new ArrayDeque<>(List.of(status(2799, 0)));
        Duration phase = Duration.ofMinutes(20);

        assertFalse(new WaitCommand(stack(answers), clock, clock.pause()).awaitAtLeast("DOWNLOADING", 2800, phase));
        assertEquals(START.plus(phase), clock.instant());
    }

    private static Answer status(int downloading, int booksToDownload) {
        return new Answer(200, TarantinoJson.mapper().valueToTree(Map.of(
                "books", Map.of("DOWNLOADING", downloading),
                "queues", Map.of("books-to-download", booksToDownload))));
    }

    private static Stack stack(Deque<Object> answers) {
        return new Stack() {
            @Override
            public Answer controlStatus() {
                Object next = answers.size() > 1 ? answers.removeFirst() : answers.getFirst();
                if (next instanceof RuntimeException failure) {
                    throw failure;
                }
                return (Answer) next;
            }

            @Override
            public Answer book(int bookId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Answer search(String query, int offset, int limit) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
