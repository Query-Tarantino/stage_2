package org.ulpgc.tarantino.control.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** The reconciliation of SPEC §5.3: its steps in their order, each idempotent, so any pass can resume from Mongo. */
public class ReconcileCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReconcileCommand.class);

    private final List<Runnable> steps;
    private final Clock clock;
    private final AtomicReference<Instant> lastReconciliation = new AtomicReference<>();

    public ReconcileCommand(List<Runnable> steps, Clock clock) {
        this.steps = List.copyOf(steps);
        this.clock = clock;
    }

    /**
     * Runs every step in order. If one fails, as when Mongo or Hazelcast do not answer, it logs it and ends the pass
     * there without throwing, so the scheduled loop keeps running.
     */
    public void execute() {
        try {
            steps.forEach(Runnable::run);
            lastReconciliation.set(clock.instant());
        } catch (RuntimeException e) {
            LOGGER.warn("Reconciliation stopped, the next pass starts over: {}", e.getMessage());
        }
    }

    /** When the last whole pass finished, as {@code GET /control/status} returns it (SPEC §6.5). */
    public Optional<Instant> lastReconciliation() {
        return Optional.ofNullable(lastReconciliation.get());
    }
}
