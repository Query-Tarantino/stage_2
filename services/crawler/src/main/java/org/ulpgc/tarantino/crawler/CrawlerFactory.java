package org.ulpgc.tarantino.crawler;

import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.crawler.adapters.datalake.batch.BatchBasedDatalakeAdapter;
import org.ulpgc.tarantino.crawler.adapters.gutenberg.GutenbergHttpDownloader;
import org.ulpgc.tarantino.crawler.adapters.gutenberg.LocalMirrorDownloader;
import org.ulpgc.tarantino.crawler.commands.IngestBookCommand;
import org.ulpgc.tarantino.crawler.ports.BookDownloader;
import org.ulpgc.tarantino.crawler.ports.DatalakeStorage;

public final class CrawlerFactory {

    private CrawlerFactory() {
    }

    public static IngestBookCommand ingestCommand(TarantinoConfig config) {
        return new IngestBookCommand(downloader(config), datalake(config));
    }

    public static BookDownloader downloader(TarantinoConfig config) {
        return config.mirror().<BookDownloader>map(LocalMirrorDownloader::new).orElseGet(GutenbergHttpDownloader::new);
    }

    public static DatalakeStorage datalake(TarantinoConfig config) {
        return new BatchBasedDatalakeAdapter(config.datalake());
    }
}
