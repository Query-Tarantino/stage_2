package org.ulpgc.tarantino.common.model;

import java.time.Instant;

public record BatchPublished(String batchId, int generation, int bookCount, Instant publishedAt) {
}
