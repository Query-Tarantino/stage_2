package org.ulpgc.tarantino.crawler;

import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.mongo.MongoIndexes;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.common.startup.Startup;
import org.ulpgc.tarantino.crawler.commands.DownloadWorker;

import java.util.List;
import java.util.stream.IntStream;

public class Main {

    public static void main(String[] args) {
        TarantinoConfig config = TarantinoConfig.fromEnvironment(TarantinoService.CRAWLER);
        Connections connections = Connections.open(config);
        Startup.start(config, connections, List.of(), CrawlerFactory.routes(connections));
        Startup.retry("create the book state indexes", () -> MongoIndexes.ensureStateIndexes(connections.database()));
        DownloadWorker worker = CrawlerFactory.downloadWorker(config, connections);
        IntStream.range(0, config.parallelDownloads())
                .forEach(index -> Thread.ofPlatform().name("download-" + index).start(worker));
    }
}
