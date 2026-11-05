package org.ulpgc.tarantino.control.ports;

import org.ulpgc.tarantino.control.model.GenerationBatch;

import java.util.List;

/** The expired {@code WRITING} batches and “Abandon a batch” (SPEC §5.1 and §5.3, step 2). */
public interface BatchLeases {

    /** The {@code WRITING} batches of every existing generation whose lease has passed by the server's time (SPEC §5.3, step 2). */
    List<GenerationBatch> expired();

    /** “Abandon a batch” (SPEC §5.1): whether it matched, false if the indexer published or renewed it first. */
    boolean abandon(GenerationBatch batch);
}
