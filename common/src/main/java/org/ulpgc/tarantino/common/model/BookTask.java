package org.ulpgc.tarantino.common.model;

import java.time.Instant;

public record BookTask(int bookId, Instant enqueuedAt) {
}
