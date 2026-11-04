package org.ulpgc.tarantino.indexer;

import io.javalin.Javalin;
import org.ulpgc.tarantino.common.config.Coordination;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.messaging.BookTasks;
import org.ulpgc.tarantino.common.messaging.Broker;
import org.ulpgc.tarantino.common.messaging.MessagingContracts;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.mongo.BookStates;
import org.ulpgc.tarantino.common.operations.CrashPoints;
import org.ulpgc.tarantino.common.operations.LeaseHeartbeat;
import org.ulpgc.tarantino.common.operations.Replica;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.common.terms.FileStopwordsLoader;
import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.indexer.adapters.datalake.batch.BatchBasedDatalakeReader;
import org.ulpgc.tarantino.indexer.adapters.broker.BrokerIndexQueue;
import org.ulpgc.tarantino.indexer.adapters.http.BookRoutes;
import org.ulpgc.tarantino.indexer.adapters.http.IndexRoutes;
import org.ulpgc.tarantino.indexer.adapters.mongo.MongoBatchStore;
import org.ulpgc.tarantino.indexer.adapters.mongo.MongoIndexStatus;
import org.ulpgc.tarantino.indexer.adapters.mongo.MongoIndexWriter;
import org.ulpgc.tarantino.indexer.adapters.mongo.MongoRebuilds;
import org.ulpgc.tarantino.indexer.commands.BatchCollector;
import org.ulpgc.tarantino.indexer.commands.BookReader;
import org.ulpgc.tarantino.indexer.commands.IndexBatchCommand;
import org.ulpgc.tarantino.indexer.commands.IndexWorker;
import org.ulpgc.tarantino.indexer.commands.PollingIndexWorker;
import org.ulpgc.tarantino.indexer.ports.BatchAnnouncer;
import org.ulpgc.tarantino.indexer.ports.BooksToIndex;
import org.ulpgc.tarantino.indexer.ports.sources.DatalakeReader;

import java.time.Clock;
import java.util.function.Consumer;

public final class IndexerFactory {

    private IndexerFactory() {
    }

    /** The batch loop of the configured mode (SPEC §11.1). */
    public static Runnable indexWorker(TarantinoConfig config, Connections connections) {
        MongoBatchStore batches = new MongoBatchStore(connections.database(), config.lease(), Replica.name());
        if (config.coordination() == Coordination.POLLING) {
            return new PollingIndexWorker(batches, indexCommand(config, connections, batches, batch -> {
            }), config.indexBatch(), config.batchTimeout(), config.poll());
        }
        Broker broker = connections.broker().orElseThrow();
        BatchCollector collector = new BatchCollector(new BrokerIndexQueue(broker.queue(MessagingContracts.BOOKS_TO_INDEX)),
                config.indexBatch(), config.batchTimeout(), Clock.systemUTC());
        return new IndexWorker(collector, indexCommand(config, connections, batches, broker.announcements()::publish));
    }

    private static IndexBatchCommand indexCommand(TarantinoConfig config, Connections connections, MongoBatchStore batches,
                                                  BatchAnnouncer announcer) {
        return new IndexBatchCommand(batches, new BookReader(datalakeReader(config), tokenizer(config)),
                new MongoIndexWriter(connections.database()), announcer, new TransitionLog(), new CrashPoints(config.crashAt()),
                renewal -> LeaseHeartbeat.start(config.heartbeat(), renewal));
    }

    public static Consumer<Javalin> routes(Connections connections) {
        IndexRoutes index = new IndexRoutes(new MongoRebuilds(connections.database()), new MongoIndexStatus(connections.database()));
        BookRoutes books = new BookRoutes(new BookStates(connections.database())::state, booksToIndex(connections),
                Clock.systemUTC());
        return app -> {
            index.register(app);
            books.register(app);
        };
    }

    /** In mode {@code polling} nothing is enqueued: a {@code DOWNLOADED} book is polled (SPEC §11.3). */
    private static BooksToIndex booksToIndex(Connections connections) {
        return connections.broker().<BooksToIndex>map(broker -> task -> offerOnOwnHandle(broker, task)).orElse(task -> {
        });
    }

    private static void offerOnOwnHandle(Broker broker, BookTask task) {
        try (BookTasks indexing = broker.queue(MessagingContracts.BOOKS_TO_INDEX)) {
            indexing.offer(task);
        }
    }

    public static DatalakeReader datalakeReader(TarantinoConfig config) {
        return new BatchBasedDatalakeReader(config.datalake());
    }

    private static Tokenizer tokenizer(TarantinoConfig config) {
        return new Tokenizer(new FileStopwordsLoader(config.workload().resolve("stopwords.txt")).stopwords());
    }
}
