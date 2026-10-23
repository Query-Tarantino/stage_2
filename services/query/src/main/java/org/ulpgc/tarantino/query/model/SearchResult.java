package org.ulpgc.tarantino.query.model;

/** A result of {@code GET /search} (SPEC §6.3): {@code language} is the first code of the book, or null. */
public record SearchResult(int bookId, String title, String author, String language, Integer year) {
}
