package org.ulpgc.tarantino.control.commands;

import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.control.model.ControlStatus;
import org.ulpgc.tarantino.control.ports.QueueSizes;
import org.ulpgc.tarantino.control.ports.StatusCounts;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** {@code GET /control/status} (SPEC §6.5): the generation, every book state, both queues and the last reconciliation. */
public class ControlStatusCommand {

    private final StatusCounts counts;
    private final QueueSizes queues;
    private final Supplier<Optional<Instant>> lastReconciliation;

    public ControlStatusCommand(StatusCounts counts, QueueSizes queues, Supplier<Optional<Instant>> lastReconciliation) {
        this.counts = counts;
        this.queues = queues;
        this.lastReconciliation = lastReconciliation;
    }

    public ControlStatus execute() {
        return new ControlStatus(counts.currentGeneration(), everyState(counts.booksByStatus()), queues.downloads(),
                queues.index(), lastReconciliation.get());
    }

    private static Map<BookStatus, Long> everyState(Map<BookStatus, Long> counted) {
        return Arrays.stream(BookStatus.values()).collect(Collectors.toMap(Function.identity(),
                status -> counted.getOrDefault(status, 0L), (first, second) -> first, () -> new EnumMap<>(BookStatus.class)));
    }
}
