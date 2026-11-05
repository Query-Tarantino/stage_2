package org.ulpgc.tarantino.indexer.model.rebuild;

import java.time.Instant;

/**
 * {@code GET /index/status} (SPEC §6.5), all for the current generation. {@code lastUpdate} is null before its first
 * published batch, and {@code rebuild} is null while no rebuild is {@code RUNNING}.
 */
public record IndexStatus(int generation, long booksIndexed, long batchesPublished, Instant lastUpdate, double indexSizeMb,
                          RebuildProgress rebuild) {
}
