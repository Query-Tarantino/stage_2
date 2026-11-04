package org.ulpgc.tarantino.crawler;

import io.javalin.Javalin;
import org.ulpgc.tarantino.common.config.CrashPoint;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.messaging.BookTasks;
import org.ulpgc.tarantino.common.messaging.Broker;
import org.ulpgc.tarantino.common.messaging.MessagingContracts;
import org.ulpgc.tarantino.common.mongo.BookStates;
import org.ulpgc.tarantino.common.operations.CrashPoints;
import org.ulpgc.tarantino.common.operations.Lease;
import org.ulpgc.tarantino.common.operations.LeaseHeartbeat;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.crawler.adapters.broker.BrokerDownloadQueue;
import org.ulpgc.tarantino.crawler.adapters.datalake.batch.BatchBasedDatalakeAdapter;
import org.ulpgc.tarantino.crawler.adapters.gutenberg.GutenbergHttpDownloader;
import org.ulpgc.tarantino.crawler.adapters.gutenberg.LocalMirrorDownloader;
import org.ulpgc.tarantino.crawler.adapters.http.IngestRoutes;
import org.ulpgc.tarantino.crawler.adapters.mongo.MongoBookClaims;
import org.ulpgc.tarantino.crawler.adapters.mongo.MongoDownloadedBooks;
import org.ulpgc.tarantino.crawler.adapters.mongo.MongoIngestRequests;
import org.ulpgc.tarantino.crawler.commands.DownloadBookCommand;
import org.ulpgc.tarantino.crawler.commands.DownloadWorker;
import org.ulpgc.tarantino.crawler.commands.IngestBookCommand;
import org.ulpgc.tarantino.crawler.ports.BookDownloader;
import org.ulpgc.tarantino.crawler.ports.DatalakeStorage;
import org.ulpgc.tarantino.crawler.ports.TaskQueue;

import java.time.Clock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

public final class CrawlerFactory {

    private CrawlerFactory() {
    }

    /** One worker for one thread, with its own queue handles (SPEC §11.1). */
    public static DownloadWorker downloadWorker(TarantinoConfig config, Connections connections) {
        Broker broker = connections.broker().orElseThrow();
        BookTasks downloads = broker.queue(MessagingContracts.BOOKS_TO_DOWNLOAD);
        BookTasks indexing = broker.queue(MessagingContracts.BOOKS_TO_INDEX);
        Consumer<CrashPoint> crashPoints = new CrashPoints(config.crashAt())::hit;
        Function<BooleanSupplier, Lease> heartbeats = renewal -> LeaseHeartbeat.start(config.heartbeat(), renewal);
        MongoBookClaims claims = new MongoBookClaims(connections.database(), config.lease(), config.maxAttempts());
        DownloadBookCommand download = new DownloadBookCommand(claims, ingestCommand(config), downloads::offer, indexing::offer,
                new TransitionLog(), Clock.systemUTC(), crashPoints, heartbeats);
        return new DownloadWorker(new BrokerDownloadQueue(downloads), download, crashPoints);
    }

    /**
     * The routes, built before the broker connects (SPEC §2.4): the queue is only looked up when {@code POST /ingest} uses
     * it, so until the broker answers that request is a {@code 503} instead of the crawler dying at startup.
     */
    public static Consumer<Javalin> routes(Connections connections) {
        return new IngestRoutes(new BookStates(connections.database())::state, new MongoIngestRequests(connections.database()),
                new MongoDownloadedBooks(connections.database()), downloadQueueOnEachUse(connections), new TransitionLog(),
                Clock.systemUTC())::register;
    }

    private static TaskQueue downloadQueueOnEachUse(Connections connections) {
        Broker broker = connections.broker().orElseThrow();
        return task -> broker.queue(MessagingContracts.BOOKS_TO_DOWNLOAD).offer(task);
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
