package org.ulpgc.tarantino.query.model;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What {@code query} can see (SPEC §7): the {@code PUBLISHED} batches of a generation and their books. It is
 * immutable, so a request reads it once and uses it whole.
 */
public record Snapshot(int generation, Set<String> batchIds, Set<Integer> bookIds) {

    public Snapshot {
        batchIds = Set.copyOf(batchIds);
        bookIds = Set.copyOf(bookIds);
    }

    public static Snapshot empty(int generation) {
        return new Snapshot(generation, Set.of(), Set.of());
    }

    /** {@code N} of TF-IDF: the books of the snapshot, never a count of the collection. */
    public int bookCount() {
        return bookIds.size();
    }

    /** The same snapshot plus some newly published batches and their books. */
    public Snapshot with(Map<String, List<Integer>> booksByBatch) {
        Set<String> batches = new HashSet<>(batchIds);
        batches.addAll(booksByBatch.keySet());
        Set<Integer> books = new HashSet<>(bookIds);
        booksByBatch.values().forEach(books::addAll);
        return new Snapshot(generation, batches, books);
    }
}
