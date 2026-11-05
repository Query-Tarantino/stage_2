package org.ulpgc.tarantino.indexer;

import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.mongo.MongoIndexes;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.common.startup.Startup;
import org.ulpgc.tarantino.indexer.adapters.local.LocalIndexQueue;

import java.util.List;

/** Starts {@code indexer} (SPEC §2.2 and §2.4): its HTTP routes and its index worker. */
public class Main {

    public static void main(String[] args) {
        TarantinoConfig config = TarantinoConfig.fromEnvironment(TarantinoService.INDEXER);
        Connections connections = Connections.open(config);
        LocalIndexQueue localIndex = new LocalIndexQueue(config.localIndexCapacity());
        Startup.start(config, connections, List.of(), IndexerFactory.routes(config, connections, localIndex));
        Startup.retry("create the book state indexes", () -> MongoIndexes.ensureStateIndexes(connections.database()));
        Thread.ofPlatform().name("index-batches").start(IndexerFactory.indexWorker(config, connections, localIndex));
    }
}
