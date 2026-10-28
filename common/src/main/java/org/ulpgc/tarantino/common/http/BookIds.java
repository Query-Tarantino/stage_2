package org.ulpgc.tarantino.common.http;

import org.ulpgc.tarantino.common.model.ProblemCode;

import java.util.regex.Pattern;

/** The {@code book_id} of every {@code {book_id}} route (SPEC §6.2): a positive integer that fits in an Int32. */
public final class BookIds {

    public static final String PARAMETER = "book_id";

    private static final Pattern AT_MOST_TEN_DIGITS_WITHOUT_LEADING_ZERO = Pattern.compile("[1-9][0-9]{0,9}");

    private BookIds() {
    }

    /** The id as received, without trimming; anything else is a {@code 400} with {@code INVALID_PARAMETER}. */
    public static int parse(String value) {
        if (value == null || !AT_MOST_TEN_DIGITS_WITHOUT_LEADING_ZERO.matcher(value).matches()
                || Long.parseLong(value) > Integer.MAX_VALUE) {
            throw new ProblemException(ProblemCode.INVALID_PARAMETER, PARAMETER,
                    PARAMETER + " must be a positive integer of at most " + Integer.MAX_VALUE);
        }
        return Integer.parseInt(value);
    }
}
