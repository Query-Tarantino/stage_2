package org.ulpgc.tarantino.control.ports;

/** “Create the state” of each seeded book (SPEC §5.1). */
public interface BookStateStore {

    /** “Create the state” (SPEC §5.1): true only if this call created it. */
    boolean create(int bookId);
}
