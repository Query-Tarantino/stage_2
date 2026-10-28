package org.ulpgc.tarantino.crawler;

import com.hazelcast.core.HazelcastInstance;
import io.javalin.Javalin;
import org.ulpgc.tarantino.common.config.CrashPoint;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.hazelcast.BookTaskQueue;
import org.ulpgc.tarantino.common.hazelcast.HazelcastContracts;
import org.ulpgc.tarantino.common.mongo.BookStates;
import org.ulpgc.tarantino.common.operations.CrashPoints;
import org.ulpgc.tarantino.common.operations.Lease;
import org.ulpgc.tarantino.common.operations.LeaseHeartbeat;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.crawler.adapters.datalake.batch.BatchBasedDatalakeAdapter;
import org.ulpgc.tarantino.crawler.adapters.gutenberg.GutenbergHttpDownloader;
import org.ulpgc.tarantino.crawler.adapters.gutenberg.LocalMirrorDownloader;
import org.ulpgc.tarantino.crawler.adapters.http.IngestRoutes;
import org.ulpgc.tarantino.crawler.adapters.mongo.MongoBookClaims;
import org.ulpgc.tarantino.crawler.commands.DownloadBookCommand;
import org.ulpgc.tarantino.crawler.commands.DownloadWorker;
import org.ulpgc.tarantino.crawler.commands.IngestBookCommand;
import org.ulpgc.tarantino.crawler.ports.BookDownloader;
import org.ulpgc.tarantino.crawler.ports.DatalakeStorage;

import java.time.Clock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

public final class CrawlerFactory {

    private CrawlerFactory() {
    }

    public static DownloadWorker downloadWorker(TarantinoConfig config, Connections connections) {
        HazelcastInstance client = connections.hazelcast().client();
        BookTaskQueue downloads = new BookTaskQueue(client, HazelcastContracts.BOOKS_TO_DOWNLOAD);
        BookTaskQueue indexing = new BookTaskQueue(client, HazelcastContracts.BOOKS_TO_INDEX);
        Consumer<CrashPoint> crashPoints = new CrashPoints(config.crashAt())::hit;
        Function<BooleanSupplier, Lease> heartbeats = renewal -> LeaseHeartbeat.start(config.heartbeat(), renewal);
        MongoBookClaims claims = new MongoBookClaims(connections.database(), config.lease(), config.maxAttempts());
        DownloadBookCommand download = new DownloadBookCommand(claims, ingestCommand(config), downloads::offer, indexing::offer,
                new TransitionLog(), Clock.systemUTC(), crashPoints, heartbeats);
        return new DownloadWorker(downloads::poll, download, crashPoints);
    }

    public static Consumer<Javalin> routes(Connections connections) {
        return new IngestRoutes(new BookStates(connections.database())::state)::register;
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
