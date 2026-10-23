package org.ulpgc.tarantino.control;

import org.ulpgc.tarantino.control.adapters.FileControlStateStore;
import org.ulpgc.tarantino.control.adapters.LocalCrawler;
import org.ulpgc.tarantino.control.adapters.LocalIndexer;
import org.ulpgc.tarantino.control.commands.ControlPipeline;
import org.ulpgc.tarantino.crawler.CrawlerConfig;
import org.ulpgc.tarantino.crawler.CrawlerFactory;
import org.ulpgc.tarantino.indexer.IndexerConfig;
import org.ulpgc.tarantino.indexer.IndexerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

public final class ControlFactory {

    private static final String DEFAULT_CANDIDATES = "sample_ids.txt";

    private ControlFactory() {
    }

    /** The pipeline of the control service, once what an interrupted run left in the datalake is removed (SPEC §6). */
    public static ControlPipeline pipeline(ControlConfig config, CrawlerConfig crawler, IndexerConfig indexer,
                                           List<Integer> candidates) {
        CrawlerFactory.removeIncompleteWrites(crawler);
        return new ControlPipeline(new FileControlStateStore(config.control()),
                new LocalCrawler(CrawlerFactory.ingestCommand(crawler),
                        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("download-", 1).factory())),
                new LocalIndexer(IndexerFactory.indexCommand(indexer)),
                candidates, config.parallelDownloads(), config.indexBatch());
    }

    /**
     * The ids of the workload file given, in order. Without one, every book of the local mirror in ascending id
     * order when there is a mirror, or else the sample dataset (SPEC §9).
     */
    public static List<Integer> candidates(ControlConfig config, CrawlerConfig crawler, Optional<String> file) {
        if (file.isEmpty() && crawler.mirror() != null) {
            return CrawlerFactory.mirrorBookIds(crawler);
        }
        return ids(config.workload().resolve(file.orElse(DEFAULT_CANDIDATES)));
    }

    private static List<Integer> ids(Path file) {
        try {
            return Files.readAllLines(file).stream()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty())
                    .map(Integer::valueOf)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
