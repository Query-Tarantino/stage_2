package org.ulpgc.tarantino.crawler;

import io.javalin.Javalin;
import org.ulpgc.tarantino.common.config.Coordination;
import org.ulpgc.tarantino.common.config.CrashPoint;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.http.InternalTasks;
import org.ulpgc.tarantino.common.messaging.BookTasks;
import org.ulpgc.tarantino.common.messaging.Broker;
import org.ulpgc.tarantino.common.messaging.MessagingContracts;
import org.ulpgc.tarantino.common.model.BookTask;
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
import org.ulpgc.tarantino.crawler.adapters.local.LocalDownloadQueue;
import org.ulpgc.tarantino.crawler.adapters.http.IngestRoutes;
import org.ulpgc.tarantino.crawler.adapters.mongo.MongoBookClaims;
import org.ulpgc.tarantino.crawler.adapters.mongo.MongoDownloadedBooks;
import org.ulpgc.tarantino.crawler.adapters.mongo.MongoIngestRequests;
import org.ulpgc.tarantino.crawler.commands.DownloadBookCommand;
import org.ulpgc.tarantino.crawler.commands.DownloadWorker;
import org.ulpgc.tarantino.crawler.commands.IngestBookCommand;
import org.ulpgc.tarantino.crawler.commands.PollingDownloadWorker;
import org.ulpgc.tarantino.crawler.ports.BookDownloader;
import org.ulpgc.tarantino.crawler.ports.DatalakeStorage;
import org.ulpgc.tarantino.crawler.ports.TaskQueue;

import java.time.Clock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/** Builds the download workers and the HTTP routes of {@code crawler} over its connections (SPEC §2.2). */
public final class CrawlerFactory {

    private CrawlerFactory() {
    }

    /**
     * One worker for one thread, with its own queue handles in the modes with a broker (SPEC §11.1). In mode
     * {@code orchestrator} every worker takes from the replica's {@code localDownloads}, and a failed download that ends
     * {@code QUEUED} goes back there; if it is full, the orchestrator dispatches the book again.
     */
    public static Runnable downloadWorker(TarantinoConfig config, Connections connections, LocalDownloadQueue localDownloads) {
        MongoBookClaims claims = new MongoBookClaims(connections.database(), config.lease(), config.maxAttempts());
        Consumer<CrashPoint> crashPoints = new CrashPoints(config.crashAt())::hit;
        if (config.coordination() == Coordination.POLLING) {
            return new PollingDownloadWorker(claims, downloadCommand(config, claims, task -> {
            }, task -> {
            }, crashPoints), config.poll());
        }
        if (config.coordination() == Coordination.ORCHESTRATOR) {
            return new DownloadWorker(localDownloads, downloadCommand(config, claims, localDownloads::offer, task -> {
            }, crashPoints), crashPoints);
        }
        Broker broker = connections.broker().orElseThrow();
        BookTasks downloads = broker.queue(MessagingContracts.BOOKS_TO_DOWNLOAD);
        BookTasks indexing = broker.queue(MessagingContracts.BOOKS_TO_INDEX);
        DownloadBookCommand download = downloadCommand(config, claims, downloads::offer, indexing::offer, crashPoints);
        return new DownloadWorker(new BrokerDownloadQueue(downloads), download, crashPoints);
    }

    private static DownloadBookCommand downloadCommand(TarantinoConfig config, MongoBookClaims claims, TaskQueue downloads,
                                                       TaskQueue indexing, Consumer<CrashPoint> crashPoints) {
        Function<BooleanSupplier, Lease> heartbeats = renewal -> LeaseHeartbeat.start(config.heartbeat(), renewal);
        return new DownloadBookCommand(claims, ingestCommand(config), downloads, indexing, new TransitionLog(), Clock.systemUTC(),
                crashPoints, heartbeats);
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

    /** The routes of the mode: in mode {@code orchestrator}, also {@code POST /internal/download} (SPEC §11.4). */
    public static Consumer<Javalin> routes(TarantinoConfig config, Connections connections, LocalDownloadQueue localDownloads) {
        Consumer<Javalin> ingest = routes(connections);
        if (config.coordination() != Coordination.ORCHESTRATOR) {
            return ingest;
        }
        return ingest.andThen(app -> InternalTasks.register(app, InternalTasks.DOWNLOAD, localDownloads::offer));
    }

    /** Without a broker nothing is enqueued: a {@code QUEUED} book is polled or dispatched (SPEC §11.3 and §11.4). */
    private static TaskQueue downloadQueueOnEachUse(Connections connections) {
        return connections.broker().<TaskQueue>map(broker -> task -> offerOnOwnHandle(broker, task)).orElse(task -> {
        });
    }

    private static void offerOnOwnHandle(Broker broker, BookTask task) {
        try (BookTasks downloads = broker.queue(MessagingContracts.BOOKS_TO_DOWNLOAD)) {
            downloads.offer(task);
        }
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
