package org.ulpgc.tarantino.common.model;

/** The states of a batch (SPEC §4.5). */
public enum BatchStatus {
    WRITING,
    PUBLISHED,
    ABANDONED
}
