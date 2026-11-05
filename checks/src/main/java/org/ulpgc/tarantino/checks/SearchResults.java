package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.ulpgc.tarantino.checks.ApiNames.BOOK_ID;

/** The {@code book_id} of each result of a {@code GET /search} answer, in its order (SPEC §6.3). */
final class SearchResults {

    private SearchResults() {
    }

    static List<Integer> bookIds(JsonNode results) {
        if (results == null || !results.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(results.spliterator(), false)
                .filter(result -> result.has(BOOK_ID))
                .map(result -> result.get(BOOK_ID).asInt())
                .toList();
    }

    static boolean containsBook(JsonNode results, int bookId) {
        return bookIds(results).contains(bookId);
    }
}
