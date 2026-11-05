package org.ulpgc.tarantino.crawler.ports;

import org.ulpgc.tarantino.crawler.model.failure.DownloadException;

/** Where the raw text of a book comes from: Project Gutenberg or the local mirror (SPEC §2.2 and §4.7). */
public interface BookDownloader {

    String rawText(int bookId) throws DownloadException;
}
