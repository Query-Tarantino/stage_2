package org.ulpgc.tarantino.control;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.mongo.MongoIndexes;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.common.startup.Startup;
import org.ulpgc.tarantino.control.commands.ReconcileCommand;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        TarantinoConfig config = TarantinoConfig.fromEnvironment(TarantinoService.CONTROL);
        Connections connections = Connections.open(config);
        Startup.start(config, connections, List.of(), routes -> {
        });
        Startup.retry("create the book state indexes", () -> MongoIndexes.ensureStateIndexes(connections.database()));
        List<Integer> candidates = ControlFactory.candidates(config);
        Startup.retry("seed the candidates", () -> seed(connections, candidates));
        reconcileOnOneThread(config, ControlFactory.reconcileCommand(config, connections));
    }

    /** The first reconciliation right after seeding and then one every TARANTINO_RECONCILE_SECONDS (SPEC §5.3). */
    private static void reconcileOnOneThread(TarantinoConfig config, ReconcileCommand reconcile) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("reconciliation").factory());
        scheduler.scheduleWithFixedDelay(reconcile::execute, 0, config.reconcile().toMillis(), TimeUnit.MILLISECONDS);
    }

    private static void seed(Connections connections, List<Integer> candidates) {
        int created = ControlFactory.seedCommand(connections).execute(candidates);
        LOGGER.info("Seeded {} new books of {} candidates", created, candidates.size());
    }
}
