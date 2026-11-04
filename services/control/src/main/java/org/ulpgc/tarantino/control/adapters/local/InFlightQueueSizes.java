package org.ulpgc.tarantino.control.adapters.local;

import org.ulpgc.tarantino.control.model.DispatchedBooks;
import org.ulpgc.tarantino.control.ports.QueueSizes;

/** The books in flight of each stage in mode {@code orchestrator}, which {@code GET /control/status} reports in {@code queues} (SPEC §11.4). */
public class InFlightQueueSizes implements QueueSizes {

    private final DispatchedBooks downloads;
    private final DispatchedBooks indexing;

    public InFlightQueueSizes(DispatchedBooks downloads, DispatchedBooks indexing) {
        this.downloads = downloads;
        this.indexing = indexing;
    }

    @Override
    public int downloads() {
        return downloads.size();
    }

    @Override
    public int index() {
        return indexing.size();
    }
}
