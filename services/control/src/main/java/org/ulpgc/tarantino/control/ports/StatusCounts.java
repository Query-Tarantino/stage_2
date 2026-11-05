package org.ulpgc.tarantino.control.ports;

import org.ulpgc.tarantino.common.model.BookStatus;

import java.util.Map;

/** The counts {@code GET /control/status} answers: books per state and the current generation (SPEC §6.5). */
public interface StatusCounts {

    /** {@code current.generation}, the one {@code query} serves. */
    int currentGeneration();

    /** How many books are in each state; a state without books may be missing. */
    Map<BookStatus, Long> booksByStatus();
}
