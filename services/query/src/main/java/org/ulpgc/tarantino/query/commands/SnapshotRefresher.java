package org.ulpgc.tarantino.query.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.query.model.Snapshot;
import org.ulpgc.tarantino.query.ports.PublishedBatches;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps the snapshot (SPEC §7). Only one thread refreshes it; requests just read the reference. When the generation
 * changes, it loads every published batch of the new one; otherwise only the batches it did not have, compared by
 * {@code _id} and never by {@code published_at}.
 */
public class SnapshotRefresher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotRefresher.class);

    private final PublishedBatches batches;
    private final AtomicReference<Snapshot> current = new AtomicReference<>();

    public SnapshotRefresher(PublishedBatches batches) {
        this.batches = batches;
    }

    /** Empty until the first refresh succeeds; until then {@code query} answers {@code 503}. */
    public Optional<Snapshot> current() {
        return Optional.ofNullable(current.get());
    }

    public void refresh() {
        int generation = batches.currentGeneration();
        Snapshot base = current().filter(snapshot -> snapshot.generation() == generation).orElseGet(() -> Snapshot.empty(generation));
        List<String> newBatches = batches.publishedBatchIds(generation).stream()
                .filter(batchId -> !base.batchIds().contains(batchId))
                .toList();
        if (newBatches.isEmpty() && current().filter(base::equals).isPresent()) {
            return;
        }
        Map<String, List<Integer>> newBooks = newBatches.isEmpty() ? Map.of() : batches.bookIds(generation, newBatches);
        current.set(base.with(newBooks));
    }

    /** A refresh that keeps the previous snapshot if Mongo fails, for the refresh thread. */
    public void refreshOrKeep() {
        try {
            refresh();
        } catch (RuntimeException e) {
            LOGGER.warn("Kept the previous snapshot: {}", e.getMessage());
        }
    }
}
