package org.ulpgc.tarantino.indexer.model.book;

/** A downloaded book as the indexer reads it from the datalake: its header and its body (SPEC §4.6). */
public record BookText(int bookId, String header, String body) {
}
