package org.ulpgc.tarantino.control;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.mongo.MongoIndexes;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.common.startup.Startup;

import java.util.List;

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
    }

    private static void seed(Connections connections, List<Integer> candidates) {
        int created = ControlFactory.seedCommand(connections).execute(candidates);
        LOGGER.info("Seeded {} new books of {} candidates", created, candidates.size());
    }
}
