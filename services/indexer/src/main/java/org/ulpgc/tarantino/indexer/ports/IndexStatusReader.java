package org.ulpgc.tarantino.indexer.ports;

import org.ulpgc.tarantino.indexer.model.rebuild.IndexStatus;

/** What {@code GET /index/status} reports about the current generation (SPEC §6.5). */
public interface IndexStatusReader {

    IndexStatus status();
}
