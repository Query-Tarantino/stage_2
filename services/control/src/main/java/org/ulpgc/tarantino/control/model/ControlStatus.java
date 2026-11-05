package org.ulpgc.tarantino.control.model;

import org.ulpgc.tarantino.common.model.BookStatus;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** What {@code GET /control/status} reports (SPEC §6.5): {@code books} always carries all six states. */
public record ControlStatus(int generation, Map<BookStatus, Long> books, int downloadQueue, int indexQueue,
                            Optional<Instant> lastReconciliation) {
}
