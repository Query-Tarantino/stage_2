package org.ulpgc.tarantino.common.messaging;

/** The message broker of a coordination mode that uses one (SPEC §11.1). */
public interface Broker {

    /** A new handle on the queue on each call. */
    BookTasks queue(String name);

    BatchAnnouncements announcements();

    /** The messages waiting in the queue, for step 4 of SPEC §5.3 and {@code GET /control/status}. */
    int size(String queue);

    boolean connected();

    /** What {@code /health} lists in {@code down} while the broker does not answer (SPEC §6.5). */
    String name();
}
