package org.ulpgc.tarantino.control.ports;

/** Asks every {@code query} replica to refresh its snapshot (SPEC §11.4). */
public interface SnapshotRefreshes {

    void refreshAll();
}
