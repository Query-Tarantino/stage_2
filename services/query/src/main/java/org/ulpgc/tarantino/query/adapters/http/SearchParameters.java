package org.ulpgc.tarantino.query.adapters.http;

import org.ulpgc.tarantino.common.http.ProblemException;
import org.ulpgc.tarantino.common.model.ProblemCode;
import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.query.model.SearchQuery;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates and parses GET /search parameters in the order of SPEC §6.3. */
public final class SearchParameters {
    private static final String Q = "q";
    private static final String AUTHOR = "author";
    private static final String LANGUAGE = "language";
    private static final String YEAR = "year";
    private static final String OFFSET = "offset";
    private static final String LIMIT = "limit";
    private static final int DEFAULT_OFFSET = 0;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;
    private static final Pattern LANGUAGE_PATTERN = Pattern.compile("^[a-z]{2}$");
    private static final Pattern YEAR_PATTERN = Pattern.compile("^[0-9]{4}$");
    private static final Pattern NON_NEGATIVE_INTEGER = Pattern.compile("^[0-9]+$");

    private SearchParameters() {
    }

    public static SearchQuery query(String q, String offset, String limit) {
        return query(new Tokenizer(Set.of()), q, null, null, null, offset, limit);
    }

    public static SearchQuery query(
            Tokenizer tokenizer,
            String q,
            String author,
            String language,
            String year,
            String offset,
            String limit) {
        validateQ(q);
        List<String> terms = authorTerms(tokenizer, author);
        String validatedLanguage = language(language);
        Integer validatedYear = year(year);
        int validatedOffset = offset(offset);
        int validatedLimit = limit(limit);
        return new SearchQuery(
                q,
                isBlank(author) ? null : author,
                terms,
                validatedLanguage,
                validatedYear,
                validatedOffset,
                validatedLimit);
    }

    private static void validateQ(String q) {
        if (isBlank(q)) {
            throw new ProblemException(ProblemCode.MISSING_PARAMETER, Q, "q is required");
        }
    }

    private static List<String> authorTerms(Tokenizer tokenizer, String author) {
        if (isBlank(author)) {
            return List.of();
        }
        List<String> terms = tokenizer.distinctTerms(author);
        if (terms.isEmpty()) {
            throw new ProblemException(
                    ProblemCode.INVALID_PARAMETER, AUTHOR, "author must contain at least one word");
        }
        return terms;
    }

    private static String language(String value) {
        if (isBlank(value)) {
            return null;
        }
        if (!LANGUAGE_PATTERN.matcher(value).matches()) {
            throw new ProblemException(
                    ProblemCode.INVALID_PARAMETER,
                    LANGUAGE,
                    "language must be a two-letter lowercase ISO 639-1 code");
        }
        return value;
    }

    private static Integer year(String value) {
        if (isBlank(value)) {
            return null;
        }
        if (!YEAR_PATTERN.matcher(value).matches()) {
            throw new ProblemException(
                    ProblemCode.INVALID_PARAMETER, YEAR, "year must be a four-digit year");
        }
        return Integer.valueOf(Integer.parseInt(value));
    }

    private static int offset(String value) {
        if (isBlank(value)) {
            return DEFAULT_OFFSET;
        }
        Integer parsed = parseNonNegativeInt(value);
        if (parsed == null) {
            throw new ProblemException(
                    ProblemCode.INVALID_PARAMETER, OFFSET, "offset must be an integer of at least 0");
        }
        return parsed;
    }

    private static int limit(String value) {
        if (isBlank(value)) {
            return DEFAULT_LIMIT;
        }
        Integer parsed = parseNonNegativeInt(value);
        if (parsed == null || !inLimitRange(parsed)) {
            throw new ProblemException(
                    ProblemCode.INVALID_PARAMETER, LIMIT, "limit must be between 1 and 100");
        }
        return parsed;
    }

    private static Integer parseNonNegativeInt(String value) {
        if (!NON_NEGATIVE_INTEGER.matcher(value).matches()) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static boolean inLimitRange(int limit) {
        return limit >= MIN_LIMIT && limit <= MAX_LIMIT;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}