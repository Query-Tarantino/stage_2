package org.ulpgc.tarantino.query.model;

import java.util.List;

/**
 * A book from {@code GET /books/{book_id}} (SPEC §6.4): the fields of a search result plus {@code languages} and
 * {@code original_year}.
 */
public record BookDetails(
        int bookId,
        String title,
        String author,
        String language,
        List<String> languages,
        Integer year,
        Integer originalYear) {

    public BookDetails {
        languages = languages == null ? List.of() : List.copyOf(languages);
    }
}
