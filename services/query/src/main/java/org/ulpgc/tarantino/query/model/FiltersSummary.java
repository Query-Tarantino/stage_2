package org.ulpgc.tarantino.query.model;

import java.util.List;

/**
 * The summary served by {@code GET /search/filters} (SPEC §6.4), computed once per snapshot.
 */
public record FiltersSummary(List<LanguageFilter> languages, YearRange years, int totalBooks) {

    public FiltersSummary {
        languages = languages == null ? List.of() : List.copyOf(languages);
    }

    public static FiltersSummary empty() {
        return new FiltersSummary(List.of(), new YearRange(null, null), 0);
    }

    public record LanguageFilter(String code, String name, int books) {
    }

    public record YearRange(Integer min, Integer max) {
    }
}
