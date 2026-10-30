package org.ulpgc.tarantino.control.commands;

import org.ulpgc.tarantino.control.ports.AbandonedBatches;

/**
 * Step 5 of the reconciliation (SPEC §5.3): deletes the postings of the batches abandoned longer than the grace period,
 * on every pass, so those a zombie indexer writes after the first deletion go too. Batches and metadata stay.
 */
public class DeleteAbandonedPostingsCommand {

    private final AbandonedBatches batches;

    public DeleteAbandonedPostingsCommand(AbandonedBatches batches) {
        this.batches = batches;
    }

    public void execute() {
        batches.pastGracePeriod().forEach(batches::deletePostings);
    }
}
