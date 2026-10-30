package org.ulpgc.tarantino.control.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.control.ports.OldGenerations;

/** Step 6.3 of the reconciliation (SPEC §5.3): drops the generations below the current one after the grace period. */
public class DropOldGenerationsCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(DropOldGenerationsCommand.class);

    private final OldGenerations generations;

    public DropOldGenerationsCommand(OldGenerations generations) {
        this.generations = generations;
    }

    public void execute() {
        generations.settledCurrent().ifPresent(this::dropBefore);
    }

    private void dropBefore(int current) {
        generations.existingBefore(current).forEach(this::drop);
    }

    private void drop(int generation) {
        generations.drop(generation);
        LOGGER.info("Dropped the collections of generation {}", generation);
    }
}
