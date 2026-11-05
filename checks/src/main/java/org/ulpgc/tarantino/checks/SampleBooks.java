package org.ulpgc.tarantino.checks;

/** The book of {@code workload/sample_ids.txt} that the checks name (SPEC §9), and ids that no book has. */
final class SampleBooks {

    static final int PRIDE_AND_PREJUDICE = 1342;
    static final int PRIDE_AND_PREJUDICE_YEAR = 1998;
    static final int MISSING_BOOK = 999_999;
    static final long BEYOND_INT32 = Integer.MAX_VALUE + 1L;

    private SampleBooks() {
    }
}
