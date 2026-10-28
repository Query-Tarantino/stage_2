package org.ulpgc.tarantino.control.model;

/** A batch of {@code batches_g<generation>}, with its {@code batch_id} as a hex string (SPEC §4.3). */
public record GenerationBatch(int generation, String batchId) {
}
