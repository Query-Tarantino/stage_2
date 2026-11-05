package org.ulpgc.tarantino.control;

import org.ulpgc.tarantino.common.config.Coordination;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.messaging.MessagingContracts;
import org.ulpgc.tarantino.common.mongo.BookStates;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.control.adapters.LocalMirror;
import org.ulpgc.tarantino.control.adapters.broker.BrokerQueueSizes;
import org.ulpgc.tarantino.control.adapters.broker.BrokerTaskQueue;
import org.ulpgc.tarantino.control.adapters.datalake.FileDatalakeTemporaries;
import org.ulpgc.tarantino.control.adapters.http.ControlStatusRoutes;
import org.ulpgc.tarantino.control.adapters.http.DnsSnapshotRefreshes;
import org.ulpgc.tarantino.control.adapters.http.HttpWorkDispatcher;
import org.ulpgc.tarantino.control.adapters.local.InFlightQueueSizes;
import org.ulpgc.tarantino.control.adapters.mongo.MongoAbandonedBatches;
import org.ulpgc.tarantino.control.adapters.mongo.MongoBatchLeases;
import org.ulpgc.tarantino.control.adapters.mongo.MongoDownloadLeases;
import org.ulpgc.tarantino.control.adapters.mongo.MongoIndexingBooks;
import org.ulpgc.tarantino.control.adapters.mongo.MongoLostMessages;
import org.ulpgc.tarantino.control.adapters.mongo.MongoOldGenerations;
import org.ulpgc.tarantino.control.adapters.mongo.MongoOutdatedBooks;
import org.ulpgc.tarantino.control.adapters.mongo.MongoPublishedBatches;
import org.ulpgc.tarantino.control.adapters.mongo.MongoQueueSizes;
import org.ulpgc.tarantino.control.adapters.mongo.MongoRebuilds;
import org.ulpgc.tarantino.control.adapters.mongo.MongoStatusCounts;
import org.ulpgc.tarantino.control.adapters.mongo.MongoWaitingBooks;
import org.ulpgc.tarantino.control.commands.AbandonExpiredBatchesCommand;
import org.ulpgc.tarantino.control.commands.AnnouncePublishedBatchesCommand;
import org.ulpgc.tarantino.control.commands.CleanDatalakeCommand;
import org.ulpgc.tarantino.control.commands.ControlStatusCommand;
import org.ulpgc.tarantino.control.commands.DeleteAbandonedPostingsCommand;
import org.ulpgc.tarantino.control.commands.DropOldGenerationsCommand;
import org.ulpgc.tarantino.control.commands.OrchestrateCommand;
import org.ulpgc.tarantino.control.commands.ReconcileCommand;
import org.ulpgc.tarantino.control.commands.ReindexOutdatedBooksCommand;
import org.ulpgc.tarantino.control.commands.ReleaseExpiredDownloadsCommand;
import org.ulpgc.tarantino.control.commands.RequeueLostMessagesCommand;
import org.ulpgc.tarantino.control.commands.ResolveIndexingBooksCommand;
import org.ulpgc.tarantino.control.commands.SeedCommand;
import org.ulpgc.tarantino.control.commands.SwitchGenerationCommand;
import org.ulpgc.tarantino.control.model.DispatchedBooks;
import org.ulpgc.tarantino.control.model.Stage;
import org.ulpgc.tarantino.control.ports.DownloadQueue;
import org.ulpgc.tarantino.control.ports.IndexQueue;
import org.ulpgc.tarantino.control.ports.QueueSizes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the commands of {@code control}, the seeding, the reconciliation, the datalake cleanup and, in mode
 * {@code orchestrator}, the orchestrator, over its Mongo connection and the broker of its mode (SPEC §2.2, §5.3 and §11).
 */
public final class ControlFactory {

    private static final String QUERY_HOST = "query";

    private ControlFactory() {
    }

    public static SeedCommand seedCommand(Connections connections) {
        return new SeedCommand(new BookStates(connections.database())::create, downloadQueue(connections),
                new TransitionLog(), Clock.systemUTC());
    }

    /** The steps of the reconciliation in the order of SPEC §5.3; step 4 only in the modes with a broker (SPEC §11.1). */
    public static ReconcileCommand reconcileCommand(TarantinoConfig config, Connections connections) {
        List<Runnable> steps = new ArrayList<>(List.of(releaseExpiredDownloads(config, connections)::execute,
                abandonExpiredBatches(connections)::execute,
                resolveIndexingBooks(connections)::execute));
        connections.broker().ifPresent(broker -> steps.add(requeueLostMessages(config, connections)::execute));
        steps.addAll(List.of(deleteAbandonedPostings(config, connections)::execute,
                reindexOutdatedBooks(connections)::execute,
                switchGeneration(connections)::execute,
                dropOldGenerations(config, connections)::execute));
        return new ReconcileCommand(steps, Clock.systemUTC());
    }

    /** The datalake cleanup of SPEC §4.6, with the clock of the container, which is the host's. */
    public static CleanDatalakeCommand cleanDatalakeCommand(TarantinoConfig config) {
        return new CleanDatalakeCommand(new FileDatalakeTemporaries(config.datalake(), config.tmpMaxAge(), Clock.systemUTC()));
    }

    public static ControlStatusRoutes statusRoutes(Connections connections, ReconcileCommand reconcile) {
        return statusRoutes(connections, reconcile, queueSizes(connections));
    }

    /** In mode {@code orchestrator}, {@code queues} reports the books in flight of each stage (SPEC §11.4). */
    public static ControlStatusRoutes statusRoutes(TarantinoConfig config, Connections connections, ReconcileCommand reconcile,
                                                   Map<Stage, DispatchedBooks> dispatched) {
        if (config.coordination() != Coordination.ORCHESTRATOR) {
            return statusRoutes(connections, reconcile);
        }
        return statusRoutes(connections, reconcile, new InFlightQueueSizes(dispatched.get(Stage.DOWNLOAD), dispatched.get(Stage.INDEX)));
    }

    private static ControlStatusRoutes statusRoutes(Connections connections, ReconcileCommand reconcile, QueueSizes sizes) {
        return new ControlStatusRoutes(new ControlStatusCommand(new MongoStatusCounts(connections.database()), sizes,
                reconcile::lastReconciliation));
    }

    /** The books the orchestrator has in flight, shared by its tick and {@code GET /control/status}. */
    public static Map<Stage, DispatchedBooks> dispatchedBooks() {
        return Map.of(Stage.DOWNLOAD, new DispatchedBooks(), Stage.INDEX, new DispatchedBooks());
    }

    /** One tick of mode {@code orchestrator} (SPEC §11.4). */
    public static OrchestrateCommand orchestrateCommand(TarantinoConfig config, Connections connections,
                                                        Map<Stage, DispatchedBooks> dispatched) {
        AnnouncePublishedBatchesCommand announce = new AnnouncePublishedBatchesCommand(new MongoPublishedBatches(connections.database()),
                new DnsSnapshotRefreshes(QUERY_HOST, TarantinoService.QUERY.defaultPort()));
        return new OrchestrateCommand(new MongoWaitingBooks(connections.database()), new HttpWorkDispatcher(config.orchestratorTarget()),
                dispatched, Map.of(Stage.DOWNLOAD, config.orchestratorDownloadWindow(), Stage.INDEX, config.orchestratorIndexWindow()),
                config.requeue(), announce::execute, new TransitionLog(), Clock.systemUTC());
    }

    /** The books to seed (SPEC §4.7): every book of the local mirror, or the ids of a workload file in its order. */
    public static List<Integer> candidates(TarantinoConfig config) {
        if (TarantinoConfig.MIRROR_CANDIDATES.equals(config.candidates())) {
            return new LocalMirror(config.mirror().orElseThrow()).bookIds();
        }
        return ids(config.workload().resolve(config.candidates()));
    }

    private static ReleaseExpiredDownloadsCommand releaseExpiredDownloads(TarantinoConfig config, Connections connections) {
        return new ReleaseExpiredDownloadsCommand(new MongoDownloadLeases(connections.database(), config.maxAttempts()),
                downloadQueue(connections), new TransitionLog(), Clock.systemUTC());
    }

    private static AbandonExpiredBatchesCommand abandonExpiredBatches(Connections connections) {
        return new AbandonExpiredBatchesCommand(new MongoBatchLeases(connections.database()), new TransitionLog());
    }

    private static ResolveIndexingBooksCommand resolveIndexingBooks(Connections connections) {
        return new ResolveIndexingBooksCommand(new MongoIndexingBooks(connections.database()), indexQueue(connections),
                new TransitionLog(), Clock.systemUTC());
    }

    private static RequeueLostMessagesCommand requeueLostMessages(TarantinoConfig config, Connections connections) {
        return new RequeueLostMessagesCommand(queueSizes(connections),
                new MongoLostMessages(connections.database(), config.requeue()), downloadQueue(connections),
                indexQueue(connections), new TransitionLog(), Clock.systemUTC());
    }

    private static DeleteAbandonedPostingsCommand deleteAbandonedPostings(TarantinoConfig config, Connections connections) {
        return new DeleteAbandonedPostingsCommand(new MongoAbandonedBatches(connections.database(), config.gc()));
    }

    private static ReindexOutdatedBooksCommand reindexOutdatedBooks(Connections connections) {
        return new ReindexOutdatedBooksCommand(new MongoOutdatedBooks(connections.database()), indexQueue(connections),
                new TransitionLog(), Clock.systemUTC());
    }

    private static SwitchGenerationCommand switchGeneration(Connections connections) {
        return new SwitchGenerationCommand(new MongoRebuilds(connections.database()));
    }

    private static DropOldGenerationsCommand dropOldGenerations(TarantinoConfig config, Connections connections) {
        return new DropOldGenerationsCommand(new MongoOldGenerations(connections.database(), config.gc()));
    }

    /** The waiting work: the broker's queues, or the waiting books in mode {@code polling} (SPEC §11.3). */
    private static QueueSizes queueSizes(Connections connections) {
        return connections.broker().<QueueSizes>map(BrokerQueueSizes::new).orElseGet(() -> new MongoQueueSizes(connections.database()));
    }

    /** Without a broker nothing is enqueued: a waiting book is polled or dispatched (SPEC §11.3 and §11.4). */
    private static DownloadQueue downloadQueue(Connections connections) {
        return connections.broker().<DownloadQueue>map(broker -> new BrokerTaskQueue(broker, MessagingContracts.BOOKS_TO_DOWNLOAD))
                .orElse(task -> {
                });
    }

    private static IndexQueue indexQueue(Connections connections) {
        return connections.broker().<IndexQueue>map(broker -> new BrokerTaskQueue(broker, MessagingContracts.BOOKS_TO_INDEX))
                .orElse(task -> {
                });
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
