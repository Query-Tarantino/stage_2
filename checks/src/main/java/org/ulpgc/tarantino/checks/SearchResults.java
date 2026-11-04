package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.stream.StreamSupport;

/** The {@code results} of a {@code GET /search} answer (SPEC §6.3). */
final class SearchResults {

    private static final String BOOK_ID = "book_id";

    private SearchResults() {
    }

    static boolean containsBook(JsonNode results, int bookId) {
        return bookIds(results).contains(bookId);
    }

    /** The {@code book_id} of each result, in page order; empty when there is no {@code results} array. */
    static List<Integer> bookIds(JsonNode results) {
        if (results == null || !results.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(results.spliterator(), false)
                .filter(result -> result.has(BOOK_ID))
                .map(result -> result.get(BOOK_ID).asInt())
                .toList();
    }
}
