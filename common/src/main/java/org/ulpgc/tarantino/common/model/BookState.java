package org.ulpgc.tarantino.common.model;

import java.time.Instant;

/** A book's state as the API returns it (SPEC §6.2); {@code lastError} is null without an error. */
public record BookState(int bookId, BookStatus status, int attempts, BookErrorCode lastError, Instant updatedAt) {
}
