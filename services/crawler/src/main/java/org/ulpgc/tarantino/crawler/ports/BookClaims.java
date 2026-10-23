package org.ulpgc.tarantino.crawler.ports;

import java.util.Optional;

/** The crawler's conditional writes on {@code book_states} (SPEC §5.1). */
public interface BookClaims {

    /** “Claim a book”: the new {@code claim_id}, or empty if the book was not {@code QUEUED}. */
    Optional<String> claim(int bookId);

    /** “Finish a download”: false if the lease was lost. */
    boolean finish(int bookId, String claimId);
}
