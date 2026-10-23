package org.ulpgc.tarantino.indexer.adapters.index;

import org.ulpgc.tarantino.indexer.model.terms.TermOccurrences;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Postings added since the last flush, drained in term order: a flush then writes the terms of each folder
 * together and inserts into MongoDB's term index in key order, and does so the same way on every run.
 */
public final class PendingPostings {

    private final Map<String, Set<Integer>> postings = new HashMap<>();

    public void add(TermOccurrences occurrences) {
        occurrences.frequencies().keySet()
                .forEach(term -> postings.computeIfAbsent(term, key -> new TreeSet<>()).add(occurrences.bookId()));
    }

    public SortedMap<String, Set<Integer>> drain() {
        SortedMap<String, Set<Integer>> drained = new TreeMap<>(postings);
        postings.clear();
        return drained;
    }
}
