package org.ulpgc.tarantino.crawler.ports;

import java.util.List;

/** The books in {@code DOWNLOADED}, {@code INDEXING} or {@code PUBLISHED}, in ascending order (SPEC §6.5). */
public interface DownloadedBooks {

    List<Integer> downloaded();
}
