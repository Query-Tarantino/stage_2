package org.ulpgc.tarantino.query.model;

import java.util.List;

/** A {@code postings_g<n>} document: the books of one batch that contain the term and each one's {@code tf}. */
public record TermPostings(String term, List<Integer> bookIds, List<Integer> tfs) {
}
