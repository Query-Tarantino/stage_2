package org.ulpgc.tarantino.control.ports;

import org.ulpgc.tarantino.control.model.Outcome;

import java.util.List;
import java.util.Map;

public interface Indexer {

    /** Indexes the books together, flushing the index once, and returns the outcome of each one by id. */
    Map<Integer, Outcome> index(List<Integer> bookIds);
}
