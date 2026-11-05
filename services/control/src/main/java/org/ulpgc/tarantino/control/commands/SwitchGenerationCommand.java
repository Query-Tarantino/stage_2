package org.ulpgc.tarantino.control.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.control.ports.Rebuilds;

/**
 * Step 6.2 of the reconciliation (SPEC §5.3): with a {@code RUNNING} rebuild, switches the pointer to its target once
 * the condition of SPEC §5.4 holds and then finishes it, or only finishes it if a crash left the pointer switched.
 */
public class SwitchGenerationCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwitchGenerationCommand.class);

    private final Rebuilds rebuilds;

    public SwitchGenerationCommand(Rebuilds rebuilds) {
        this.rebuilds = rebuilds;
    }

    public void execute() {
        rebuilds.runningTarget().ifPresent(this::advanceTo);
    }

    private void advanceTo(int target) {
        int current = rebuilds.currentGeneration();
        if (current == target || current == target - 1 && switchedTo(target)) {
            finish(target);
        }
    }

    private boolean switchedTo(int target) {
        if (!rebuilds.readyToSwitch(target) || !rebuilds.switchTo(target)) {
            return false;
        }
        LOGGER.info("Switched the current generation to {}", target);
        return true;
    }

    private void finish(int target) {
        if (rebuilds.finish(target)) {
            LOGGER.info("Finished the rebuild into generation {}", target);
        }
    }
}
