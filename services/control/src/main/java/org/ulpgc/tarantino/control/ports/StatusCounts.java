package org.ulpgc.tarantino.control.ports;

import org.ulpgc.tarantino.common.model.BookStatus;

import java.util.Map;

public interface StatusCounts {

    /** {@code current.generation}, the one {@code query} serves. */
    int currentGeneration();

    /** How many books are in each state; a state without books may be missing. */
    Map<BookStatus, Long> booksByStatus();
}
