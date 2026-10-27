package org.ulpgc.tarantino.query;

import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.hazelcast.BatchPublishedTopic;
import org.ulpgc.tarantino.common.http.Dependency;
import org.ulpgc.tarantino.common.http.Health;
import org.ulpgc.tarantino.common.mongo.IndexState;
import org.ulpgc.tarantino.common.mongo.MongoIndexes;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.common.startup.Startup;
import org.ulpgc.tarantino.query.commands.SnapshotRefresher;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) {
        TarantinoConfig config = TarantinoConfig.fromEnvironment(TarantinoService.QUERY);
        Connections connections = Connections.open(config);
        SnapshotRefresher snapshots = QueryFactory.snapshotRefresher(connections);
        Dependency snapshot = new Dependency(Health.SNAPSHOT, () -> snapshots.current().isPresent());
        Startup.start(config, connections, List.of(snapshot), QueryFactory.searchRoutes(config, connections, snapshots)::register);
        Startup.retry("create the generation indexes", () -> {
            int generation = new IndexState(connections.database()).currentGeneration();
            MongoIndexes.ensureGenerationIndexes(connections.database(), generation);
        });
        refreshOnOneThread(config, connections, snapshots);
    }

    /** At startup, every TARANTINO_SNAPSHOT_SECONDS and on each BatchPublished, which only asks the thread to refresh (SPEC §7). */
    private static void refreshOnOneThread(TarantinoConfig config, Connections connections, SnapshotRefresher snapshots) {
        ScheduledExecutorService refresher = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("snapshot").factory());
        refresher.scheduleWithFixedDelay(snapshots::refreshOrKeep, 0, config.snapshot().toMillis(), TimeUnit.MILLISECONDS);
        new BatchPublishedTopic(connections.hazelcast().client()).listen(batch -> refresher.execute(snapshots::refreshOrKeep));
    }
}
