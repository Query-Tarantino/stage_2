package org.ulpgc.tarantino.common.messaging;

import org.ulpgc.tarantino.common.activemq.ActiveMqBroker;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.hazelcast.HazelcastBroker;
import org.ulpgc.tarantino.common.hazelcast.HazelcastClients;

import java.util.Optional;

public final class Brokers {

    private Brokers() {
    }

    /**
     * The broker of the configured mode, connecting in the background, or empty in the modes without one
     * (SPEC §11.1).
     */
    public static Optional<Broker> open(TarantinoConfig config) {
        return switch (config.coordination()) {
            case HAZELCAST -> Optional.of(HazelcastBroker.of(HazelcastClients.connect(config.hazelcastMembers(),
                    config.hazelcastCluster())));
            case ACTIVEMQ -> Optional.of(ActiveMqBroker.connect(config.activeMq(), config.activeMqPersistent(), config.indexBatch()));
            case POLLING, ORCHESTRATOR -> Optional.empty();
        };
    }
}
