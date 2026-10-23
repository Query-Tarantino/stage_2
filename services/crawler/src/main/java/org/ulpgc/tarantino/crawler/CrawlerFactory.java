package org.ulpgc.tarantino.crawler;

import org.ulpgc.tarantino.crawler.adapters.datalake.batch.BatchBasedDatalakeAdapter;
import org.ulpgc.tarantino.crawler.adapters.datalake.book.BookBasedDatalakeAdapter;
import org.ulpgc.tarantino.crawler.adapters.datalake.time.TimeBasedDatalakeAdapter;
import org.ulpgc.tarantino.crawler.adapters.gutenberg.GutenbergHttpDownloader;
import org.ulpgc.tarantino.crawler.adapters.gutenberg.LocalMirrorDownloader;
import org.ulpgc.tarantino.crawler.commands.IngestBookCommand;
import org.ulpgc.tarantino.crawler.ports.BookDownloader;
import org.ulpgc.tarantino.crawler.ports.DatalakeStorage;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class CrawlerFactory {

    private static final Map<String, Function<Path, DatalakeStorage>> DATALAKE_LAYOUTS = Map.of(
            "time", TimeBasedDatalakeAdapter::new,
            "book", BookBasedDatalakeAdapter::new,
            "batch", BatchBasedDatalakeAdapter::new);

    private CrawlerFactory() {
    }

    public static IngestBookCommand ingestCommand(CrawlerConfig config) {
        return new IngestBookCommand(downloader(config), datalake(config));
    }

    /** Run once before ingesting, so an interrupted previous run leaves nothing behind (SPEC §6). */
    public static void removeIncompleteWrites(CrawlerConfig config) {
        int removed = datalake(config).removeIncompleteWrites();
        if (removed > 0) {
            System.out.println("Removed " + removed + " files left by an interrupted run from " + config.datalake());
        }
    }

    public static BookDownloader downloader(CrawlerConfig config) {
        return config.mirror() == null ? new GutenbergHttpDownloader() : new LocalMirrorDownloader(config.mirror());
    }

    public static DatalakeStorage datalake(CrawlerConfig config) {
        return option(DATALAKE_LAYOUTS, config.datalakeLayout(), "datalake layout").apply(config.datalake());
    }

    private static <T> T option(Map<String, T> options, String name, String kind) {
        return Optional.ofNullable(options.get(name))
                .orElseThrow(() -> new IllegalArgumentException("Unknown " + kind + ": " + name));
    }
}
