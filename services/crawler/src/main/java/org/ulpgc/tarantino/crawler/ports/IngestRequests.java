package org.ulpgc.tarantino.crawler.ports;

/** The conditional writes of {@code POST /ingest/{book_id}} on {@code book_states} (SPEC §5.1 and §6.2). */
public interface IngestRequests {

    /** “Create the state”: true only for the call that created it, which is the only one that enqueues it. */
    boolean create(int bookId);

    /** “Retry a {@code FAILED} book”: back to {@code QUEUED} with no attempts; false if the book was not {@code FAILED}. */
    boolean retry(int bookId);
}
