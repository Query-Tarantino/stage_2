package org.ulpgc.tarantino.query.adapters.http;

import org.ulpgc.tarantino.common.http.ProblemException;
import org.ulpgc.tarantino.common.model.ProblemCode;
import org.ulpgc.tarantino.query.model.SearchQuery;

import java.util.regex.Pattern;

/** The {@code GET /search} parameters of SPEC §6.3, checked in their order; a blank optional one counts as absent. */
final class SearchParameters {

    static final String Q = "q";
    static final String OFFSET = "offset";
    static final String LIMIT = "limit";

    private static final int DEFAULT_OFFSET = 0;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;
    private static final Pattern NON_NEGATIVE_INTEGER = Pattern.compile("[0-9]{1,9}");

    private SearchParameters() {
    }

    static SearchQuery query(String q, String offset, String limit) {
        if (q == null || q.isBlank()) {
            throw new ProblemException(ProblemCode.MISSING_PARAMETER, Q, "q is required");
        }
        return new SearchQuery(q, offset(offset), limit(limit));
    }

    private static int offset(String value) {
        if (isBlank(value)) {
            return DEFAULT_OFFSET;
        }
        if (!NON_NEGATIVE_INTEGER.matcher(value).matches()) {
            throw new ProblemException(ProblemCode.INVALID_PARAMETER, OFFSET, "offset must be an integer of at least 0");
        }
        return Integer.parseInt(value);
    }

    private static int limit(String value) {
        if (isBlank(value)) {
            return DEFAULT_LIMIT;
        }
        if (!NON_NEGATIVE_INTEGER.matcher(value).matches() || !inLimitRange(Integer.parseInt(value))) {
            throw new ProblemException(ProblemCode.INVALID_PARAMETER, LIMIT,
                    "limit must be between " + MIN_LIMIT + " and " + MAX_LIMIT);
        }
        return Integer.parseInt(value);
    }

    private static boolean inLimitRange(int limit) {
        return limit >= MIN_LIMIT && limit <= MAX_LIMIT;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
