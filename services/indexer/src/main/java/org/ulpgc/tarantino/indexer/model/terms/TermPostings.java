package org.ulpgc.tarantino.indexer.model.terms;

import java.util.List;

/**
 * A {@code postings_g<n>} document without its batch (SPEC §4.3): the batch's books that contain the term, ascending,
 * and each one's {@code tf} at the same position.
 */
public record TermPostings(String term, List<Integer> bookIds, List<Integer> tfs) {
}
