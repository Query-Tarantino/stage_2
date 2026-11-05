package org.ulpgc.tarantino.indexer.model.book;

import java.util.Map;

/** What a batch keeps of a book once read: its metadata and the frequency of each term of its body. */
public record ParsedBook(BookMetadata metadata, Map<String, Integer> frequencies) {
}
