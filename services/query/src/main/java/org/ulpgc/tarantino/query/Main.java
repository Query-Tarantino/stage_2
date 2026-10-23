package org.ulpgc.tarantino.query;

import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.common.startup.Startup;

import java.util.List;

public class Main {

    public static void main(String[] args) {
        TarantinoConfig config = TarantinoConfig.fromEnvironment(TarantinoService.QUERY);
        Connections connections = Connections.open(config);
        Startup.start(config, connections, List.of(), routes -> {
        });
    }
}
