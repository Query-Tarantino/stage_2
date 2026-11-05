package org.ulpgc.tarantino.control.commands;

import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.operations.Transition;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.control.model.GenerationBatch;
import org.ulpgc.tarantino.control.ports.BatchLeases;

/** Step 2 of the reconciliation (SPEC §5.3): abandons every expired batch of every existing generation. */
public class AbandonExpiredBatchesCommand {

    private final BatchLeases leases;
    private final TransitionLog log;

    public AbandonExpiredBatchesCommand(BatchLeases leases, TransitionLog log) {
        this.leases = leases;
        this.log = log;
    }

    public void execute() {
        leases.expired().stream()
                .filter(leases::abandon)
                .forEach(this::abandoned);
    }

    private void abandoned(GenerationBatch batch) {
        log.write(Transition.batch(batch.batchId(), BatchStatus.WRITING, BatchStatus.ABANDONED, batch.generation()));
    }
}
