package org.ulpgc.tarantino.query.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A validated {@code GET /search} request (SPEC §6.3): {@code q} as received, optional filters, and the page to answer.
 */
public record SearchQuery(
        String q,
        String author,
        List<String> authorTerms,
        String language,
        Integer year,
        int offset,
        int limit) {

    public SearchQuery {
        authorTerms = authorTerms == null ? List.of() : List.copyOf(authorTerms);
    }

    public SearchQuery(String q, int offset, int limit) {
        this(q, null, List.of(), null, null, offset, limit);
    }

    public boolean hasFilters() {
        return !authorTerms.isEmpty() || language != null || year != null;
    }

    public Map<String, Object> filters() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (author != null) {
            map.put("author", author);
        }
        if (language != null) {
            map.put("language", language);
        }
        if (year != null) {
            map.put("year", year);
        }
        return Collections.unmodifiableMap(map);
    }
}
