package org.ulpgc.tarantino.indexer.ports;

import org.ulpgc.tarantino.common.model.BatchPublished;

/** The {@code published-batches} topic (SPEC §4.2). */
public interface BatchAnnouncer {

    void announce(BatchPublished batch);
}
