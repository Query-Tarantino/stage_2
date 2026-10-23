package org.ulpgc.tarantino.query.model;

/** A validated {@code GET /search} (SPEC §6.3): {@code q} as received and the page to answer. */
public record SearchQuery(String q, int offset, int limit) {
}
