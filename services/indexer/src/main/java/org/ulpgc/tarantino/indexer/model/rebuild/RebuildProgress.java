package org.ulpgc.tarantino.indexer.model.rebuild;

import org.ulpgc.tarantino.common.model.RebuildStatus;

/** A running rebuild in {@code GET /index/status}: {@code booksPending} still block its switch (SPEC §5.4 and §6.5). */
public record RebuildProgress(int targetGeneration, RebuildStatus status, long booksPending) {
}
