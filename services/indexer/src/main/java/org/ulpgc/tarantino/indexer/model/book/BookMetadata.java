package org.ulpgc.tarantino.indexer.model.book;

import java.util.List;

/** A book's document in {@code metadata_g<n>} (SPEC §4.3); a missing field is null, a missing list empty. */
public record BookMetadata(int bookId, String title, String author, List<String> authorTerms, List<String> languages,
                           List<String> languageNames, Integer year, Integer originalYear) {
}
