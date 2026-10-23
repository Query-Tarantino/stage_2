package org.ulpgc.tarantino.query.ports;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** What the snapshot is read from: the generation pointer and the {@code PUBLISHED} batches (SPEC §7). */
public interface PublishedBatches {

    int currentGeneration();

    List<String> publishedBatchIds(int generation);

    /** The {@code book_ids} of each of the given batches. */
    Map<String, List<Integer>> bookIds(int generation, Collection<String> batchIds);
}
