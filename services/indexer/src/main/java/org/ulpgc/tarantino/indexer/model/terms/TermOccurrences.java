package org.ulpgc.tarantino.indexer.model.terms;

import java.util.Map;

public record TermOccurrences(int bookId, Map<String, Integer> frequencies) {
}
