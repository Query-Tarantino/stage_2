package org.ulpgc.tarantino.control.ports;

import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.control.model.Stage;

/** Hands a book to a worker of a stage through its internal route (SPEC §11.4). */
public interface WorkDispatcher {

    /** True if a replica took it with {@code 202}; false on {@code 503}, a timeout or any error. */
    boolean dispatch(Stage stage, BookTask task);
}
