package org.ulpgc.tarantino.control;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.config.Coordination;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.mongo.MongoIndexes;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.common.startup.Startup;
import org.ulpgc.tarantino.control.commands.CleanDatalakeCommand;
import org.ulpgc.tarantino.control.commands.OrchestrateCommand;
import org.ulpgc.tarantino.control.commands.ReconcileCommand;
import org.ulpgc.tarantino.control.model.DispatchedBooks;
import org.ulpgc.tarantino.control.model.Stage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        TarantinoConfig config = TarantinoConfig.fromEnvironment(TarantinoService.CONTROL);
        Connections connections = Connections.open(config);
        ReconcileCommand reconcile = ControlFactory.reconcileCommand(config, connections);
        Map<Stage, DispatchedBooks> dispatched = ControlFactory.dispatchedBooks();
        Startup.start(config, connections, List.of(), ControlFactory.statusRoutes(config, connections, reconcile, dispatched)::register);
        Startup.retry("create the book state indexes", () -> MongoIndexes.ensureStateIndexes(connections.database()));
        List<Integer> candidates = ControlFactory.candidates(config);
        Startup.retry("seed the candidates", () -> seed(connections, candidates));
        scheduleOnOneThread(config, reconcile, ControlFactory.cleanDatalakeCommand(config));
        if (config.coordination() == Coordination.ORCHESTRATOR) {
            orchestrateOnItsOwnThread(config, ControlFactory.orchestrateCommand(config, connections, dispatched));
        }
    }

    /** One tick every TARANTINO_ORCHESTRATOR_TICK_MILLIS on the thread orchestrator (SPEC §11.4). */
    private static void orchestrateOnItsOwnThread(TarantinoConfig config, OrchestrateCommand orchestrate) {
        ScheduledExecutorService orchestrator = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("orchestrator").factory());
        orchestrator.scheduleWithFixedDelay(orchestrate::tick, 0, config.orchestratorTick().toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * The first reconciliation and datalake cleanup right after seeding, and then one every TARANTINO_RECONCILE_SECONDS
     * and every TARANTINO_CLEANUP_SECONDS (SPEC §4.6 and §5.3), on one thread so they never overlap.
     */
    private static void scheduleOnOneThread(TarantinoConfig config, ReconcileCommand reconcile, CleanDatalakeCommand cleanDatalake) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("reconciliation").factory());
        scheduler.scheduleWithFixedDelay(reconcile::execute, 0, config.reconcile().toMillis(), TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(cleanDatalake::execute, 0, config.cleanup().toMillis(), TimeUnit.MILLISECONDS);
    }

    private static void seed(Connections connections, List<Integer> candidates) {
        int created = ControlFactory.seedCommand(connections).execute(candidates);
        LOGGER.info("Seeded {} new books of {} candidates", created, candidates.size());
    }
}
