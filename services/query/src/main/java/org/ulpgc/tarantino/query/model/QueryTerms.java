package org.ulpgc.tarantino.query.model;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class QueryTerms {

    private static final Pattern TERM = Pattern.compile("\\p{L}+");
    private static final int MIN_TERM_LENGTH = 2;

    private QueryTerms() {
    }

    public static Set<String> of(String query, Set<String> stopwords) {
        return TERM.matcher(query.toLowerCase(Locale.ROOT)).results()
                .map(MatchResult::group)
                .filter(term -> isSearchable(term, stopwords))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean isSearchable(String term, Set<String> stopwords) {
        return term.codePointCount(0, term.length()) >= MIN_TERM_LENGTH && !stopwords.contains(term);
    }
}
