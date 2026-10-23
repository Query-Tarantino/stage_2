package org.ulpgc.tarantino.query.ports;

import org.ulpgc.tarantino.query.model.SearchResult;

import java.util.Collection;
import java.util.List;

public interface MetadataReader {

    /** The results of the given books, read with a single {@code {_id: {$in: page}}} query, in any order. */
    List<SearchResult> results(int generation, Collection<Integer> bookIds);
}
