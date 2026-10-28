package org.ulpgc.tarantino.indexer;

import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.mongo.MongoIndexes;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.common.startup.Startup;

import java.util.List;

public class Main {

    public static void main(String[] args) {
        TarantinoConfig config = TarantinoConfig.fromEnvironment(TarantinoService.INDEXER);
        Connections connections = Connections.open(config);
        Startup.start(config, connections, List.of(), IndexerFactory.indexRoutes(connections)::register);
        Startup.retry("create the book state indexes", () -> MongoIndexes.ensureStateIndexes(connections.database()));
        Thread.ofPlatform().name("index-batches").start(IndexerFactory.indexWorker(config, connections));
    }
}
