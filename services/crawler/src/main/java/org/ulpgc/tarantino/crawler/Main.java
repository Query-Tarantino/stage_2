package org.ulpgc.tarantino.crawler;

import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.mongo.MongoIndexes;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.common.startup.Startup;
import org.ulpgc.tarantino.common.startup.WorkerThreads;
import org.ulpgc.tarantino.crawler.adapters.local.LocalDownloadQueue;

import java.util.List;
import java.util.stream.IntStream;

/** Starts {@code crawler} (SPEC §2.2 and §2.4): its HTTP routes and {@code TARANTINO_PARALLEL_DOWNLOADS} download workers. */
public class Main {

    public static void main(String[] args) {
        TarantinoConfig config = TarantinoConfig.fromEnvironment(TarantinoService.CRAWLER);
        Connections connections = Connections.open(config);
        LocalDownloadQueue localDownloads = new LocalDownloadQueue(config.localDownloadCapacity());
        Startup.start(config, connections, List.of(), CrawlerFactory.routes(config, connections, localDownloads));
        Startup.retry("create the book state indexes", () -> MongoIndexes.ensureStateIndexes(connections.database()));
        IntStream.range(0, config.parallelDownloads()).forEach(index -> WorkerThreads.start("download-" + index,
                CrawlerFactory.downloadWorker(config, connections, localDownloads)));
    }
}
