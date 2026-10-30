package org.ulpgc.tarantino.control.ports;

public interface QueueSizes {

    /** Messages waiting in {@code books-to-download} (SPEC §4.2). */
    int downloads();

    /** Messages waiting in {@code books-to-index} (SPEC §4.2). */
    int index();
}
