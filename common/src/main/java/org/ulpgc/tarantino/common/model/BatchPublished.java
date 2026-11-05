package org.ulpgc.tarantino.common.model;

import java.time.Instant;

/** The message of the {@code published-batches} topic (SPEC §4.2). */
public record BatchPublished(String batchId, int generation, int bookCount, Instant publishedAt) {
}
