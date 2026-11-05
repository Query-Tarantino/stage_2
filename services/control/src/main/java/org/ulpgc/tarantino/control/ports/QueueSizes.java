package org.ulpgc.tarantino.control.ports;

/** The sizes of the two queues, for step 4 of the reconciliation and {@code GET /control/status} (SPEC §5.3 and §6.5). */
public interface QueueSizes {

    /** Messages waiting in {@code books-to-download} (SPEC §4.2). */
    int downloads();

    /** Messages waiting in {@code books-to-index} (SPEC §4.2). */
    int index();
}
