package org.ulpgc.tarantino.common.hazelcast;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientConnectionStrategyConfig;
import com.hazelcast.client.config.ClientConnectionStrategyConfig.ReconnectMode;
import com.hazelcast.config.ListenerConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class HazelcastClients {

    /**
     * Long enough to never give up, yet finite: Hazelcast keeps its infinite timeout, {@code -1}, as
     * {@code Long.MAX_VALUE}, so a clock that steps back between two attempts overflows the wait into a negative one
     * and the client shuts down.
     */
    private static final long RETRY_FOR_TEN_YEARS = Duration.ofDays(3_650).toMillis();

    private HazelcastClients() {
    }

    /**
     * A client of the cluster that connects in the background and keeps retrying, at startup and whenever
     * Hazelcast goes down (SPEC §2.4 and §4.2). Until it connects, its operations throw
     * {@code HazelcastClientOfflineException} and it reports itself not connected, so a service can open its HTTP
     * port first.
     */
    public static HazelcastConnection connect(List<String> members, String cluster) {
        ConnectionState state = new ConnectionState();
        ClientConfig config = config(members, cluster);
        config.addListenerConfig(new ListenerConfig(state));
        return new HazelcastConnection(HazelcastClient.newHazelcastClient(config), state);
    }

    static ClientConfig config(List<String> members, String cluster) {
        ClientConfig config = new ClientConfig().setClusterName(cluster).setProperty("hazelcast.logging.type", "slf4j");
        config.getNetworkConfig().setAddresses(new ArrayList<>(members));
        ClientConnectionStrategyConfig connection = config.getConnectionStrategyConfig()
                .setAsyncStart(true)
                .setReconnectMode(ReconnectMode.ASYNC);
        connection.getConnectionRetryConfig().setClusterConnectTimeoutMillis(RETRY_FOR_TEN_YEARS);
        return config;
    }
}
