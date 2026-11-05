package org.ulpgc.tarantino.bench.recovery;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotWaitTest {

    private static final Instant START = Instant.parse("2026-10-21T10:00:00Z");
    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    private final MovingClock clock = new MovingClock(START);

    @Test
    void waitsUntilEveryReplicaHoldsTheBooks() {
        QueryReplicas replicas = replicas(List.of(20, 0), List.of(20, 19), List.of(20, 20));

        assertTrue(new SnapshotWait(replicas, clock, clock.pause()).awaitAtLeast(20, TIMEOUT));
        assertEquals(START.plus(WaitCommand.POLL.multipliedBy(2)), clock.instant());
    }

    @Test
    void keepsWaitingWhileNoReplicaAnswersAndGivesUpAfterTheTimeout() {
        assertFalse(new SnapshotWait(replicas(List.of()), clock, clock.pause()).awaitAtLeast(20, TIMEOUT));
        assertEquals(START.plus(TIMEOUT), clock.instant());
    }

    @SafeVarargs
    private static QueryReplicas replicas(List<Integer>... answers) {
        Deque<List<Integer>> remaining = new ArrayDeque<>(List.of(answers));
        return () -> remaining.size() > 1 ? remaining.removeFirst() : remaining.getFirst();
    }
}
