package org.ulpgc.tarantino.common.hazelcast;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientConnectionStrategyConfig;
import com.hazelcast.client.config.ClientConnectionStrategyConfig.ReconnectMode;
import com.hazelcast.config.ListenerConfig;

import java.util.ArrayList;
import java.util.List;

public final class HazelcastClients {

    private static final long RETRY_WITHOUT_LIMIT = -1;

    private HazelcastClients() {
    }

    /**
     * A client of the cluster that connects in the background and retries without limit, at startup and whenever
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
        connection.getConnectionRetryConfig().setClusterConnectTimeoutMillis(RETRY_WITHOUT_LIMIT);
        return config;
    }
}
