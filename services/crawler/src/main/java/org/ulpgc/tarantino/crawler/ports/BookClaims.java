package org.ulpgc.tarantino.crawler.ports;

import org.ulpgc.tarantino.common.model.BookErrorCode;
import org.ulpgc.tarantino.common.model.BookStatus;

import java.util.Optional;

/** The crawler's conditional writes on {@code book_states} (SPEC §5.1). */
public interface BookClaims {

    /** “Claim a book”: the new {@code claim_id}, or empty if the book was not {@code QUEUED}. */
    Optional<String> claim(int bookId);

    /** “Renew a book”: false if the lease was lost. */
    boolean renew(int bookId, String claimId);

    /** “Finish a download”: false if the lease was lost. */
    boolean finish(int bookId, String claimId);

    /** “Fail a download”: the status the book ended in, {@code QUEUED} or {@code FAILED}, or empty if the lease was lost. */
    Optional<BookStatus> fail(int bookId, String claimId, BookErrorCode reason);
}
