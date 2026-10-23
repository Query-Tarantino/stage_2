package org.ulpgc.tarantino.indexer.ports.datamarts;

import org.ulpgc.tarantino.indexer.model.terms.TermOccurrences;

public interface InvertedIndexStorage {

    void add(TermOccurrences occurrences);

    void flush();
}
