package org.ulpgc.tarantino.control.commands;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.operations.TransitionLog;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeedCommandTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:05:00Z");

    private final Set<Integer> states = new HashSet<>();
    private final List<BookTask> queue = new ArrayList<>();
    private final SeedCommand seed = new SeedCommand(states::add, queue::add, new TransitionLog(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void enqueuesOnlyTheBooksItCreates() {
        states.add(84);

        int created = seed.execute(List.of(1342, 84, 11));

        assertEquals(2, created);
        assertEquals(List.of(new BookTask(1342, NOW), new BookTask(11, NOW)), queue);
    }

    @Test
    void seedingAgainEnqueuesNothing() {
        seed.execute(List.of(1342, 84));
        queue.clear();

        assertEquals(0, seed.execute(List.of(1342, 84)));
        assertEquals(List.of(), queue);
    }
}
