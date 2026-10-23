package org.ulpgc.tarantino.indexer.ports.datamarts;

import org.ulpgc.tarantino.indexer.model.terms.TermOccurrences;

public interface InvertedIndexStorage {

    /**
     * Loads from storage whatever the structure keeps in memory, before the first book is added. Adding loads
     * it anyway; opening first lets the cost of loading be paid, or measured, apart from indexing.
     */
    default void open() {
    }

    void add(TermOccurrences occurrences);

    void flush();
}
