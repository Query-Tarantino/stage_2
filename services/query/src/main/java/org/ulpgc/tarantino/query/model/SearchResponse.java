package org.ulpgc.tarantino.query.model;

import java.util.List;
import java.util.Map;

/** The body of {@code GET /search} (SPEC §6.3); {@code total} counts every match, not only the page. */
public record SearchResponse(String query, Map<String, Object> filters, int total, int offset, int limit, List<SearchResult> results) {
}
