package org.ulpgc.tarantino.indexer;

import com.hazelcast.core.HazelcastInstance;
import io.javalin.Javalin;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.hazelcast.BatchPublishedTopic;
import org.ulpgc.tarantino.common.mongo.BookStates;
import org.ulpgc.tarantino.common.operations.CrashPoints;
import org.ulpgc.tarantino.common.operations.LeaseHeartbeat;
import org.ulpgc.tarantino.common.operations.Replica;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.common.terms.FileStopwordsLoader;
import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.indexer.adapters.datalake.batch.BatchBasedDatalakeReader;
import org.ulpgc.tarantino.indexer.adapters.hazelcast.HazelcastIndexQueue;
import org.ulpgc.tarantino.indexer.adapters.http.BookRoutes;
import org.ulpgc.tarantino.indexer.adapters.http.IndexRoutes;
import org.ulpgc.tarantino.indexer.adapters.mongo.MongoBatchStore;
import org.ulpgc.tarantino.indexer.adapters.mongo.MongoIndexWriter;
import org.ulpgc.tarantino.indexer.adapters.mongo.MongoRebuilds;
import org.ulpgc.tarantino.indexer.commands.BatchCollector;
import org.ulpgc.tarantino.indexer.commands.BookReader;
import org.ulpgc.tarantino.indexer.commands.IndexBatchCommand;
import org.ulpgc.tarantino.indexer.commands.IndexWorker;
import org.ulpgc.tarantino.indexer.ports.sources.DatalakeReader;

import java.time.Clock;
import java.util.function.Consumer;

public final class IndexerFactory {

    private IndexerFactory() {
    }

    public static IndexWorker indexWorker(TarantinoConfig config, Connections connections) {
        HazelcastInstance client = connections.hazelcast().client();
        BatchCollector collector = new BatchCollector(new HazelcastIndexQueue(client), config.indexBatch(), config.batchTimeout(),
                Clock.systemUTC());
        IndexBatchCommand index = new IndexBatchCommand(new MongoBatchStore(connections.database(), config.lease(), Replica.name()),
                new BookReader(datalakeReader(config), tokenizer(config)), new MongoIndexWriter(connections.database()),
                new BatchPublishedTopic(client)::publish, new TransitionLog(), new CrashPoints(config.crashAt()),
                renewal -> LeaseHeartbeat.start(config.heartbeat(), renewal));
        return new IndexWorker(collector, index);
    }

    public static Consumer<Javalin> routes(Connections connections) {
        IndexRoutes index = new IndexRoutes(new MongoRebuilds(connections.database()));
        BookRoutes books = new BookRoutes(new BookStates(connections.database())::state);
        return app -> {
            index.register(app);
            books.register(app);
        };
    }

    public static DatalakeReader datalakeReader(TarantinoConfig config) {
        return new BatchBasedDatalakeReader(config.datalake());
    }

    private static Tokenizer tokenizer(TarantinoConfig config) {
        return new Tokenizer(new FileStopwordsLoader(config.workload().resolve("stopwords.txt")).stopwords());
    }
}
