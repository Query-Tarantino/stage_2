package org.ulpgc.tarantino.query.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.operations.SnapshotLog;
import org.ulpgc.tarantino.query.model.Snapshot;
import org.ulpgc.tarantino.query.model.Snapshot.BookFilter;
import org.ulpgc.tarantino.query.ports.MetadataReader;
import org.ulpgc.tarantino.query.ports.PublishedBatches;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps the snapshot (SPEC §7). Only one thread refreshes it; requests just read the reference. When the generation
 * changes, it loads every published batch of the new one; otherwise only the batches it did not have, compared by
 * {@code _id} and never by {@code published_at}. Each batch that enters the snapshot leaves its {@code snapshot} line
 * (SPEC §4.8) once the new snapshot is set.
 */
public class SnapshotRefresher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotRefresher.class);

    private final PublishedBatches batches;
    private final MetadataReader metadata;
    private final SnapshotLog log;
    private final AtomicReference<Snapshot> current = new AtomicReference<>();

    public SnapshotRefresher(PublishedBatches batches, MetadataReader metadata, SnapshotLog log) {
        this.batches = batches;
        this.metadata = metadata;
        this.log = log;
    }

    public SnapshotRefresher(PublishedBatches batches, MetadataReader metadata) {
        this(batches, metadata, new SnapshotLog());
    }

    /** Empty until the first refresh succeeds; until then {@code query} answers {@code 503}. */
    public Optional<Snapshot> current() {
        return Optional.ofNullable(current.get());
    }

    public void refresh() {
        int generation = batches.currentGeneration();
        Snapshot base = current().filter(snapshot -> snapshot.generation() == generation)
                .orElseGet(() -> Snapshot.empty(generation));
        List<String> newBatches = batches.publishedBatchIds(generation).stream()
                .filter(batchId -> !base.batchIds().contains(batchId))
                .toList();
        if (newBatches.isEmpty() && current().filter(base::equals).isPresent()) {
            return;
        }
        Map<String, List<Integer>> newBooks = newBatches.isEmpty() ? Map.of() : batches.bookIds(generation, newBatches);
        List<Integer> allNewBookIds = newBooks.values().stream().flatMap(List::stream).distinct().toList();
        Map<Integer, BookFilter> newFilters = allNewBookIds.isEmpty() ? Map.of() : metadata.filters(generation, allNewBookIds);
        current.set(base.with(newBooks, newFilters));
        newBatches.forEach(batchId -> log.write(batchId, generation));
    }

    /** A refresh that keeps the previous snapshot if Mongo fails, for the refresh thread. */
    public void refreshOrKeep() {
        try {
            refresh();
        } catch (RuntimeException exception) {
            LOGGER.warn("Kept the previous snapshot: {}", exception.getMessage());
        }
    }
}
