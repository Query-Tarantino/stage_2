package org.ulpgc.tarantino.common.model;

import java.time.Instant;

/** The message of the {@code books-to-download} and {@code books-to-index} queues (SPEC §4.2). */
public record BookTask(int bookId, Instant enqueuedAt) {
}
