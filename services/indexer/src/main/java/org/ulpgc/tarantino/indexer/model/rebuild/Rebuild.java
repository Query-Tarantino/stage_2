package org.ulpgc.tarantino.indexer.model.rebuild;

import org.ulpgc.tarantino.common.model.RebuildStatus;

/** A rebuild as {@code POST /index/rebuild} answers it: the generation served, the one being built and its state (SPEC §6.5). */
public record Rebuild(int generation, int targetGeneration, RebuildStatus status) {
}
