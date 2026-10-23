package org.ulpgc.tarantino.control.adapters;

import org.ulpgc.tarantino.control.model.Outcome;
import org.ulpgc.tarantino.control.ports.Crawler;
import org.ulpgc.tarantino.crawler.commands.DownloadResult;
import org.ulpgc.tarantino.crawler.commands.IngestBookCommand;
import org.ulpgc.tarantino.crawler.commands.IngestResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Looks books up in the caller's thread, downloads them with the given executor and stores them when asked. */
public class LocalCrawler implements Crawler {

    private final IngestBookCommand ingest;
    private final Executor downloads;

    public LocalCrawler(IngestBookCommand ingest, Executor downloads) {
        this.ingest = ingest;
        this.downloads = downloads;
    }

    @Override
    public CompletableFuture<Download> ingest(int bookId) {
        return ingest.stored(bookId)
                .map(stored -> CompletableFuture.<Download>completedFuture(() -> outcome(stored)))
                .orElseGet(() -> CompletableFuture.supplyAsync(() -> storable(ingest.download(bookId)), downloads));
    }

    private Download storable(DownloadResult download) {
        return () -> outcome(ingest.store(download));
    }

    private static Outcome outcome(IngestResult result) {
        return result.succeeded()
                ? Outcome.success("stored in " + result.paths().body().getParent())
                : Outcome.failure("skipped, " + result.failure());
    }
}
