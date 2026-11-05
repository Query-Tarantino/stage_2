package org.ulpgc.tarantino.indexer.model.batch;

/** An open batch: its {@code batch_id}, the {@code claim_id} of its lease and its write generation (SPEC §4.3). */
public record Batch(String batchId, String claimId, int generation) {
}
