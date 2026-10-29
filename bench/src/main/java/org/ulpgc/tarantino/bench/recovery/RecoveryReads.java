package org.ulpgc.tarantino.bench.recovery;

import java.util.List;

/** The checks of SPEC §8 that only MongoDB can answer, all on the current generation. */
public interface RecoveryReads {

    /** Every book with a state, by ascending id. */
    List<Integer> seededBooks();

    /** The books in more than one {@code PUBLISHED} batch, by ascending id. */
    List<Integer> booksInTwoPublishedBatches();

    /** Of {@code terms}, the ones the text of {@code bookId} contains, in the same order. */
    List<String> termsInText(int bookId, List<String> terms);

    long postingsOfAbandonedBatches();
}
