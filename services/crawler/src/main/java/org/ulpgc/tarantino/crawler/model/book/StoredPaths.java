package org.ulpgc.tarantino.crawler.model.book;

import java.nio.file.Path;

/** The final header and body paths of a book in the datalake (SPEC §4.6). */
public record StoredPaths(Path header, Path body) {
}
