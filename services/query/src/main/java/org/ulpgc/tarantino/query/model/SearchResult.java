package org.ulpgc.tarantino.query.model;

import java.util.List;

public record SearchResult(String query, List<BookMetadata> books) {
}
