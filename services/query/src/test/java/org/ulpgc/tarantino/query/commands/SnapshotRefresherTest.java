package org.ulpgc.tarantino.query.commands;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.query.model.SearchResult;
import org.ulpgc.tarantino.query.model.Snapshot;
import org.ulpgc.tarantino.query.model.Snapshot.BookFilter;
import org.ulpgc.tarantino.query.ports.MetadataReader;
import org.ulpgc.tarantino.query.ports.PublishedBatches;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnapshotRefresherTest {

    private final FakeBatches batches = new FakeBatches();
    private final SnapshotRefresher refresher = new SnapshotRefresher(batches);

    @Test
    void hasNoSnapshotUntilItsFirstRefresh() {
        assertEquals(Optional.empty(), refresher.current());

        refresher.refresh();

        assertEquals(Optional.of(Snapshot.empty(1)), refresher.current());
    }

    @Test
    void loadsOnlyTheBatchesItDidNotHaveComparedById() {
        batches.publish(1, "a", List.of(1342, 84));
        refresher.refresh();
        batches.publish(1, "b", List.of(11));
        batches.requested.clear();

        refresher.refresh();

        assertEquals(new Snapshot(1, Set.of("a", "b"), Set.of(1342, 84, 11)), refresher.current().orElseThrow());
        assertEquals(List.of(List.of("b")), batches.requested);
    }

    @Test
    void readsFilterMetadataForNewlyPublishedBatches() {
        MetadataReader metadata = new MetadataReader() {
            @Override
            public List<SearchResult> results(int generation, Collection<Integer> bookIds) {
                return List.of();
            }

            @Override
            public Map<Integer, BookFilter> filters(int generation, Collection<Integer> bookIds) {
                return Map.of(
                        1342, new BookFilter(List.of("en"), 1998),
                        84, new BookFilter(List.of("en", "de"), 1818));
            }
        };
        SnapshotRefresher customRefresher = new SnapshotRefresher(batches, metadata);
        batches.publish(1, "a", List.of(1342, 84));

        customRefresher.refresh();

        Snapshot snapshot = customRefresher.current().orElseThrow();
        assertEquals(2, snapshot.bookCount());
        assertEquals(2, snapshot.filtersSummary().totalBooks());
        assertEquals(1818, snapshot.filtersSummary().years().min());
        assertEquals(1998, snapshot.filtersSummary().years().max());
        assertEquals(2, snapshot.filtersSummary().languages().getFirst().books());
        assertEquals("en", snapshot.filtersSummary().languages().getFirst().code());
    }

    @Test
    void swapsTheWholeSnapshotWhenTheGenerationChanges() {
        batches.publish(1, "a", List.of(1342, 84));
        refresher.refresh();
        batches.publish(2, "c", List.of(1342));
        batches.generation = 2;

        refresher.refresh();

        assertEquals(new Snapshot(2, Set.of("c"), Set.of(1342)), refresher.current().orElseThrow());
    }

    @Test
    void keepsThePreviousSnapshotWhenMongoFails() {
        batches.publish(1, "a", List.of(1342));
        refresher.refresh();
        batches.failing = true;

        refresher.refreshOrKeep();

        assertEquals(new Snapshot(1, Set.of("a"), Set.of(1342)), refresher.current().orElseThrow());
    }

    private static final class FakeBatches implements PublishedBatches {

        private final Map<Integer, Map<String, List<Integer>>> published = new HashMap<>();
        private final List<List<String>> requested = new ArrayList<>();
        private int generation = 1;
        private boolean failing;

        void publish(int batchGeneration, String batchId, List<Integer> bookIds) {
            published.computeIfAbsent(batchGeneration, key -> new LinkedHashMap<>()).put(batchId, bookIds);
        }

        @Override
        public int currentGeneration() {
            if (failing) {
                throw new IllegalStateException("Mongo does not answer");
            }
            return generation;
        }

        @Override
        public List<String> publishedBatchIds(int batchGeneration) {
            return List.copyOf(published.getOrDefault(batchGeneration, Map.of()).keySet());
        }

        @Override
        public Map<String, List<Integer>> bookIds(int batchGeneration, Collection<String> batchIds) {
            requested.add(List.copyOf(batchIds));
            Map<String, List<Integer>> books = new HashMap<>();
            batchIds.forEach(batchId -> books.put(batchId, published.get(batchGeneration).get(batchId)));
            return books;
        }
    }
}
