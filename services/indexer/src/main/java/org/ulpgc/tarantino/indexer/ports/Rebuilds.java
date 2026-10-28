package org.ulpgc.tarantino.indexer.ports;

import org.ulpgc.tarantino.indexer.model.rebuild.Rebuild;

/** The rebuild of the index into a new generation, in {@code index_state} (SPEC §5.4). */
public interface Rebuilds {

    /** “Open a rebuild” of the generation after the current one, or the rebuild that is already {@code RUNNING}. */
    Rebuild open();
}
