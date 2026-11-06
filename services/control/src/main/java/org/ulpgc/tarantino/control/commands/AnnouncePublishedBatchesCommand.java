package org.ulpgc.tarantino.control.commands;

import org.ulpgc.tarantino.control.ports.PublishedBatches;
import org.ulpgc.tarantino.control.ports.SnapshotRefreshes;

import java.util.HashSet;
import java.util.Set;

/**
 * The last step of an orchestrator tick (SPEC §11.4): if any batch of the current generation was published since the
 * last tick, compared by {@code _id}, it asks every {@code query} replica to refresh once, since one refresh takes in
 * every new batch. The set it already announced is emptied when the generation changes.
 */
public class AnnouncePublishedBatchesCommand {

    private final PublishedBatches batches;
    private final SnapshotRefreshes refreshes;
    private final Set<String> announced = new HashSet<>();
    private int announcedGeneration;

    public AnnouncePublishedBatchesCommand(PublishedBatches batches, SnapshotRefreshes refreshes) {
        this.batches = batches;
        this.refreshes = refreshes;
    }

    public void execute() {
        int generation = batches.currentGeneration();
        if (generation != announcedGeneration) {
            announced.clear();
            announcedGeneration = generation;
        }
        if (announced.addAll(batches.publishedBatchIds(generation))) {
            refreshes.refreshAll();
        }
    }
}
