package org.ulpgc.tarantino.crawler.commands;

import org.ulpgc.tarantino.common.model.BookErrorCode;
import org.ulpgc.tarantino.crawler.model.book.GutenbergText;
import org.ulpgc.tarantino.crawler.model.failure.DownloadException;
import org.ulpgc.tarantino.crawler.ports.BookDownloader;
import org.ulpgc.tarantino.crawler.ports.DatalakeStorage;

import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Ingests a book: looks it up in the datalake and, if it is not there, downloads, splits and stores it. A book is
 * in the datalake once its body exists (SPEC §4.6).
 */
public class IngestBookCommand {

    private final BookDownloader downloader;
    private final DatalakeStorage datalake;

    public IngestBookCommand(BookDownloader downloader, DatalakeStorage datalake) {
        this.downloader = downloader;
        this.datalake = datalake;
    }

    public IngestResult execute(int bookId) {
        return stored(bookId).orElseGet(() -> store(download(bookId)));
    }

    /** The book, if the datalake already holds it: it is not downloaded again. */
    public Optional<IngestResult> stored(int bookId) {
        return datalake.pathsOf(bookId).map(paths -> IngestResult.success(bookId, paths));
    }

    public DownloadResult download(int bookId) {
        try {
            return DownloadResult.success(GutenbergText.bookText(bookId, downloader.rawText(bookId)));
        } catch (DownloadException e) {
            return DownloadResult.failure(bookId, e.reason());
        }
    }

    /** Stores a downloaded book in the datalake; a failed download stores nothing. */
    public IngestResult store(DownloadResult download) {
        if (!download.succeeded()) {
            return IngestResult.failure(download.bookId(), download.failure());
        }
        try {
            return IngestResult.success(download.bookId(), datalake.save(download.text()));
        } catch (UncheckedIOException e) {
            return IngestResult.failure(download.bookId(), BookErrorCode.STORAGE_ERROR);
        }
    }
}
