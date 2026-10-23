package org.ulpgc.tarantino.crawler;

import org.ulpgc.tarantino.crawler.adapters.datalake.batch.BatchBasedDatalakeAdapter;
import org.ulpgc.tarantino.crawler.adapters.gutenberg.GutenbergHttpDownloader;
import org.ulpgc.tarantino.crawler.adapters.gutenberg.LocalMirrorDownloader;
import org.ulpgc.tarantino.crawler.commands.IngestBookCommand;
import org.ulpgc.tarantino.crawler.ports.BookDownloader;
import org.ulpgc.tarantino.crawler.ports.DatalakeStorage;

public final class CrawlerFactory {

    private CrawlerFactory() {
    }

    public static IngestBookCommand ingestCommand(CrawlerConfig config) {
        return new IngestBookCommand(downloader(config), datalake(config));
    }

    public static BookDownloader downloader(CrawlerConfig config) {
        return config.mirror() == null ? new GutenbergHttpDownloader() : new LocalMirrorDownloader(config.mirror());
    }

    public static DatalakeStorage datalake(CrawlerConfig config) {
        return new BatchBasedDatalakeAdapter(config.datalake());
    }
}
