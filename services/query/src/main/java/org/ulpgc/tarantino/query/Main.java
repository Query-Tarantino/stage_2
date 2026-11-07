package org.ulpgc.tarantino.query;

import io.javalin.Javalin;
import org.ulpgc.tarantino.common.config.Coordination;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.http.Dependency;
import org.ulpgc.tarantino.common.http.Health;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.common.startup.Startup;
import org.ulpgc.tarantino.common.startup.WorkerThreads;
import org.ulpgc.tarantino.query.adapters.http.SnapshotRefreshRoute;
import org.ulpgc.tarantino.query.commands.SnapshotRefresher;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Starts {@code query} (SPEC §2.2 and §2.4): its HTTP routes, the snapshot thread and the {@code published-batches} listener. */
public class Main {

    public static void main(String[] args) {
        TarantinoConfig config = TarantinoConfig.fromEnvironment(TarantinoService.QUERY);
        Connections connections = Connections.open(config);
        SnapshotRefresher snapshots = QueryFactory.snapshotRefresher(connections);
        ScheduledExecutorService refresher = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("snapshot").factory());
        Runnable refresh = WorkerThreads.exitingIfItDies(snapshots::refreshOrKeep);
        Runnable askForRefresh = () -> refresher.execute(refresh);
        Dependency snapshot = new Dependency(Health.SNAPSHOT, () -> snapshots.current().isPresent());
        Startup.start(config, connections, List.of(snapshot), routes(config, connections, snapshots, askForRefresh));
        refreshOnOneThread(config, connections, refresh, refresher, askForRefresh);
    }

    /**
     * The search routes and, in mode {@code orchestrator}, the internal refresh route (SPEC §11.4). The search routes read
     * {@code stopwords.txt} only once {@link Startup#start} registers them, so a missing file stops the service with code 1
     * instead of leaving a JVM without its port (SPEC §2.4).
     */
    private static Consumer<Javalin> routes(TarantinoConfig config, Connections connections, SnapshotRefresher snapshots,
                                            Runnable askForRefresh) {
        Consumer<Javalin> search = app -> QueryFactory.searchRoutes(config, connections, snapshots).register(app);
        if (config.coordination() != Coordination.ORCHESTRATOR) {
            return search;
        }
        return search.andThen(new SnapshotRefreshRoute(askForRefresh)::register);
    }

    /**
     * At startup, every TARANTINO_SNAPSHOT_SECONDS and, when the mode has a broker and TARANTINO_SNAPSHOT_ON_PUBLISH is
     * true, on each BatchPublished, which only asks the thread to refresh (SPEC §7 and §11.1).
     */
    private static void refreshOnOneThread(TarantinoConfig config, Connections connections, Runnable refresh,
                                           ScheduledExecutorService refresher, Runnable askForRefresh) {
        refresher.scheduleWithFixedDelay(refresh, 0, config.snapshot().toMillis(), TimeUnit.MILLISECONDS);
        connections.broker()
                .filter(broker -> config.snapshotOnPublish())
                .ifPresent(broker -> broker.announcements().listen(batch -> askForRefresh.run()));
    }
}
