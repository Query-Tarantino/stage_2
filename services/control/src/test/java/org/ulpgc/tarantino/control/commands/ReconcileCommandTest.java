package org.ulpgc.tarantino.control.commands;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReconcileCommandTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:06:30Z");

    private final List<String> stepsRun = new ArrayList<>();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void runsTheStepsInOrderAndRemembersWhenThePassFinished() {
        ReconcileCommand reconcile = new ReconcileCommand(List.of(step("downloads"), step("batches"), step("books")), clock);

        reconcile.execute();

        assertEquals(List.of("downloads", "batches", "books"), stepsRun);
        assertEquals(Optional.of(NOW), reconcile.lastReconciliation());
    }

    @Test
    void aFailingStepStopsThePassWithoutThrowingOrCountingIt() {
        ReconcileCommand reconcile = new ReconcileCommand(List.of(step("downloads"), mongoDown(), step("books")), clock);

        reconcile.execute();

        assertEquals(List.of("downloads"), stepsRun);
        assertEquals(Optional.empty(), reconcile.lastReconciliation());
    }

    @Test
    void theNextPassStartsOverFromTheFirstStep() {
        ReconcileCommand reconcile = new ReconcileCommand(List.of(step("downloads"), mongoDown()), clock);

        reconcile.execute();
        reconcile.execute();

        assertEquals(List.of("downloads", "downloads"), stepsRun);
    }

    private Runnable step(String name) {
        return () -> stepsRun.add(name);
    }

    private static Runnable mongoDown() {
        return () -> {
            throw new IllegalStateException("MongoDB does not answer");
        };
    }
}
