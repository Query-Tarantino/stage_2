package org.ulpgc.tarantino.control.ports;

public interface BookStateStore {

    /** “Create the state” (SPEC §5.1): true only if this call created it. */
    boolean create(int bookId);
}
