package org.ulpgc.tarantino.control.ports;

import java.util.Set;

/** The published batches of the current generation, which the orchestrator announces (SPEC §11.4). */
public interface PublishedBatches {

    int currentGeneration();

    Set<String> publishedBatchIds(int generation);
}
