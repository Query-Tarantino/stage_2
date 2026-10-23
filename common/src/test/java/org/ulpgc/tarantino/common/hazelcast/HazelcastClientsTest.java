package org.ulpgc.tarantino.common.hazelcast;

import com.hazelcast.client.HazelcastClientOfflineException;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientConnectionStrategyConfig.ReconnectMode;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HazelcastClientsTest {

    private static final String CLUSTER = "test-" + UUID.randomUUID();

    private final List<HazelcastInstance> instances = new ArrayList<>();

    @AfterEach
    void shutDown() {
        instances.forEach(HazelcastInstance::shutdown);
    }

    @Test
    void retriesInTheBackgroundWithoutLimit() {
        ClientConfig config = HazelcastClients.config(List.of("hazelcast:5701"), HazelcastContracts.DEFAULT_CLUSTER);

        assertEquals(HazelcastContracts.DEFAULT_CLUSTER, config.getClusterName());
        assertEquals(List.of("hazelcast:5701"), config.getNetworkConfig().getAddresses());
        assertTrue(config.getConnectionStrategyConfig().isAsyncStart());
        assertEquals(ReconnectMode.ASYNC, config.getConnectionStrategyConfig().getReconnectMode());
        assertEquals(-1, config.getConnectionStrategyConfig().getConnectionRetryConfig().getClusterConnectTimeoutMillis());
    }

    @Test
    void startsBeforeTheMemberAndConnectsWhenItComesUp() throws IOException, InterruptedException {
        int port = freePort();
        HazelcastConnection connection = HazelcastClients.connect(List.of("127.0.0.1:" + port), CLUSTER);
        started(connection.client());
        assertFalse(connection.connected());
        HazelcastInstance member = started(Hazelcast.newHazelcastInstance(member(port)));

        offerWhenConnected(connection.client(), "{\"book_id\":1342}");

        assertTrue(becomesTrue(connection::connected));
        assertEquals("{\"book_id\":1342}", member.getQueue(HazelcastContracts.BOOKS_TO_DOWNLOAD).poll());
    }

    private HazelcastInstance started(HazelcastInstance instance) {
        instances.addFirst(instance);
        return instance;
    }

    private static Config member(int port) {
        Config config = new Config().setClusterName(CLUSTER).setProperty("hazelcast.logging.type", "none");
        config.getNetworkConfig().setPort(port).setPortAutoIncrement(false);
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        return config;
    }

    private static void offerWhenConnected(HazelcastInstance client, String message) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(30);
        while (true) {
            try {
                client.getQueue(HazelcastContracts.BOOKS_TO_DOWNLOAD).offer(message);
                return;
            } catch (HazelcastClientOfflineException e) {
                if (Instant.now().isAfter(deadline)) {
                    throw e;
                }
                Thread.sleep(100);
            }
        }
    }

    private static boolean becomesTrue(BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(10);
        while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }
        return condition.getAsBoolean();
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
