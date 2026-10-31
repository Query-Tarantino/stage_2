package org.ulpgc.tarantino.bench.recovery;

import org.ulpgc.tarantino.common.hazelcast.HazelcastContracts;
import org.ulpgc.tarantino.common.model.BookStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

/**
 * {@code bench wait <state|queue> [<minimum> [<timeout seconds>]]} (SPEC §8): returns as soon as
 * {@code GET /control/status} shows at least that many books in that state or messages in that queue, {@link #ANY} by
 * default, so a script can cause its failure while there is work in flight or start measuring once the work it needs is
 * ready. It gives up after the timeout, {@link #DEFAULT_TIMEOUT} unless a whole phase needs longer.
 */
public class WaitCommand {

    public static final int ANY = 1;
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    static final Duration POLL = Duration.ofMillis(200);

    private static final String BOOKS = "books";
    private static final String QUEUES = "queues";

    private final Stack stack;
    private final Clock clock;
    private final Pause pause;

    public WaitCommand(Stack stack, Clock clock, Pause pause) {
        this.stack = stack;
        this.clock = clock;
        this.pause = pause;
    }

    /** {@code books} for a book state, {@code queues} for a queue, nothing for anything else. */
    public static Optional<String> sectionOf(String target) {
        if (Arrays.stream(BookStatus.values()).anyMatch(state -> state.name().equals(target))) {
            return Optional.of(BOOKS);
        }
        if (target.equals(HazelcastContracts.BOOKS_TO_DOWNLOAD) || target.equals(HazelcastContracts.BOOKS_TO_INDEX)) {
            return Optional.of(QUEUES);
        }
        return Optional.empty();
    }

    /** Whether {@code target} reached {@code minimum} within {@code timeout}; {@code target} must have a section. */
    public boolean awaitAtLeast(String target, int minimum, Duration timeout) {
        String section = sectionOf(target).orElseThrow();
        Instant deadline = clock.instant().plus(timeout);
        while (clock.instant().isBefore(deadline)) {
            if (count(section, target) >= minimum) {
                return true;
            }
            pause.sleep(POLL);
        }
        return false;
    }

    private int count(String section, String target) {
        try {
            Answer status = stack.controlStatus();
            return status.ok() ? status.body().path(section).path(target).asInt() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
