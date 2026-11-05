package org.ulpgc.tarantino.crawler.model.book;

/** A Gutenberg book split into its header and its body, the two files of the datalake (SPEC §4.6). */
public record BookText(int bookId, String header, String body) {
}
