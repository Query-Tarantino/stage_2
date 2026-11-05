package org.ulpgc.tarantino.common.model;

/** The states of a book (SPEC §4.5). */
public enum BookStatus {
    QUEUED,
    DOWNLOADING,
    DOWNLOADED,
    INDEXING,
    PUBLISHED,
    FAILED
}
