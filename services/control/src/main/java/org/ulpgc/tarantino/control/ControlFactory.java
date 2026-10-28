package org.ulpgc.tarantino.control;

import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.hazelcast.BookTaskQueue;
import org.ulpgc.tarantino.common.hazelcast.HazelcastContracts;
import org.ulpgc.tarantino.common.mongo.BookStates;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.control.adapters.LocalMirror;
import org.ulpgc.tarantino.control.adapters.hazelcast.HazelcastQueueSizes;
import org.ulpgc.tarantino.control.adapters.http.ControlStatusRoutes;
import org.ulpgc.tarantino.control.adapters.mongo.MongoAbandonedBatches;
import org.ulpgc.tarantino.control.adapters.mongo.MongoBatchLeases;
import org.ulpgc.tarantino.control.adapters.mongo.MongoDownloadLeases;
import org.ulpgc.tarantino.control.adapters.mongo.MongoIndexingBooks;
import org.ulpgc.tarantino.control.adapters.mongo.MongoLostMessages;
import org.ulpgc.tarantino.control.adapters.mongo.MongoOldGenerations;
import org.ulpgc.tarantino.control.adapters.mongo.MongoOutdatedBooks;
import org.ulpgc.tarantino.control.adapters.mongo.MongoRebuilds;
import org.ulpgc.tarantino.control.adapters.mongo.MongoStatusCounts;
import org.ulpgc.tarantino.control.commands.AbandonExpiredBatchesCommand;
import org.ulpgc.tarantino.control.commands.ControlStatusCommand;
import org.ulpgc.tarantino.control.commands.DeleteAbandonedPostingsCommand;
import org.ulpgc.tarantino.control.commands.DropOldGenerationsCommand;
import org.ulpgc.tarantino.control.commands.ReconcileCommand;
import org.ulpgc.tarantino.control.commands.ReindexOutdatedBooksCommand;
import org.ulpgc.tarantino.control.commands.ReleaseExpiredDownloadsCommand;
import org.ulpgc.tarantino.control.commands.RequeueLostMessagesCommand;
import org.ulpgc.tarantino.control.commands.ResolveIndexingBooksCommand;
import org.ulpgc.tarantino.control.commands.SeedCommand;
import org.ulpgc.tarantino.control.commands.SwitchGenerationCommand;
import org.ulpgc.tarantino.control.ports.DownloadQueue;
import org.ulpgc.tarantino.control.ports.IndexQueue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

public final class ControlFactory {

    private ControlFactory() {
    }

    public static SeedCommand seedCommand(Connections connections) {
        return new SeedCommand(new BookStates(connections.database())::create, downloadQueue(connections),
                new TransitionLog(), Clock.systemUTC());
    }

    /** The steps of the reconciliation in the order of SPEC §5.3. */
    public static ReconcileCommand reconcileCommand(TarantinoConfig config, Connections connections) {
        return new ReconcileCommand(List.of(
                releaseExpiredDownloads(config, connections)::execute,
                abandonExpiredBatches(connections)::execute,
                resolveIndexingBooks(connections)::execute,
                requeueLostMessages(config, connections)::execute,
                deleteAbandonedPostings(config, connections)::execute,
                reindexOutdatedBooks(connections)::execute,
                switchGeneration(connections)::execute,
                dropOldGenerations(config, connections)::execute), Clock.systemUTC());
    }

    public static ControlStatusRoutes statusRoutes(Connections connections, ReconcileCommand reconcile) {
        return new ControlStatusRoutes(new ControlStatusCommand(new MongoStatusCounts(connections.database()),
                new HazelcastQueueSizes(connections.hazelcast().client()), reconcile::lastReconciliation));
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
        return new RequeueLostMessagesCommand(new HazelcastQueueSizes(connections.hazelcast().client()),
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

    private static DownloadQueue downloadQueue(Connections connections) {
        return task -> bookTasks(connections, HazelcastContracts.BOOKS_TO_DOWNLOAD).offer(task);
    }

    private static IndexQueue indexQueue(Connections connections) {
        return task -> bookTasks(connections, HazelcastContracts.BOOKS_TO_INDEX).offer(task);
    }

    /** Looked up on every message, so the commands can be built before the Hazelcast client connects. */
    private static BookTaskQueue bookTasks(Connections connections, String queue) {
        return new BookTaskQueue(connections.hazelcast().client(), queue);
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
