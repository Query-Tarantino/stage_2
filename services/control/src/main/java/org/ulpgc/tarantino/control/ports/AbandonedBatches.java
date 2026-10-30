package org.ulpgc.tarantino.control.ports;

import org.ulpgc.tarantino.control.model.GenerationBatch;

import java.util.List;

public interface AbandonedBatches {

    /**
     * The {@code ABANDONED} batches of every existing generation whose {@code abandoned_at} is older than
     * {@code $$NOW − TARANTINO_GC_SECONDS} (SPEC §5.3, step 5).
     */
    List<GenerationBatch> pastGracePeriod();

    /** Deletes every posting the batch wrote in {@code postings_g<generation>}, also those written late. */
    void deletePostings(GenerationBatch batch);
}
