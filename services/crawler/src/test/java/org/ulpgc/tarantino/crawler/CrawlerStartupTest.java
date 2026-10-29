package org.ulpgc.tarantino.crawler;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.hazelcast.HazelcastContracts;
import org.ulpgc.tarantino.common.http.TarantinoServer;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;
import org.ulpgc.tarantino.common.startup.Connections;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crawler starts while Hazelcast does not listen yet (SPEC §2.4): its routes are built without touching Hazelcast,
 * {@code /health} and {@code POST /ingest} answer {@code 503}, and once Hazelcast is up the same routes enqueue.
 */
class CrawlerStartupTest {

    private static final Duration CONNECT_DEADLINE = Duration.ofSeconds(60);
    private static final Duration POLL_PAUSE = Duration.ofMillis(200);

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    private final HttpClient client = HttpClient.newHttpClient();
    private final String cluster = "test-" + UUID.randomUUID();
    private Connections connections;
    private Javalin app;
    private HazelcastInstance member;

    @AfterEach
    void stop() {
        app.stop();
        connections.hazelcast().client().shutdown();
        if (member != null) {
            member.shutdown();
        }
        client.close();
    }

    @Test
    void answersUnavailableWhileHazelcastIsDownAndEnqueuesOnceItIsUp() throws Exception {
        int hazelcastPort = freePort();
        TarantinoConfig config = TarantinoConfig.from(Map.of(
                "TARANTINO_PORT", String.valueOf(freePort()),
                "TARANTINO_MONGO_URI", mongo.uri(),
                "TARANTINO_HAZELCAST", "localhost:" + hazelcastPort,
                "TARANTINO_HAZELCAST_CLUSTER", cluster), TarantinoService.CRAWLER);
        connections = Connections.open(config);

        app = TarantinoServer.start(config, connections.dependencies(), CrawlerFactory.routes(connections));

        assertEquals(503, send("GET", "/health").statusCode());
        assertEquals(503, send("POST", "/ingest/1342").statusCode());

        member = startMember(hazelcastPort);
        awaitConnected();

        assertEquals(202, send("POST", "/ingest/84").statusCode());
        assertTrue(member.getQueue(HazelcastContracts.BOOKS_TO_DOWNLOAD).size() >= 1);
    }

    private HazelcastInstance startMember(int port) {
        Config config = new Config().setClusterName(cluster).setProperty("hazelcast.logging.type", "none");
        config.getNetworkConfig().setPort(port).setPortAutoIncrement(false);
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        return Hazelcast.newHazelcastInstance(config);
    }

    private void awaitConnected() throws InterruptedException {
        Instant deadline = Instant.now().plus(CONNECT_DEADLINE);
        while (!connections.hazelcast().connected()) {
            assertTrue(Instant.now().isBefore(deadline), "The crawler never connected to Hazelcast");
            Thread.sleep(POLL_PAUSE);
        }
    }

    private HttpResponse<String> send(String method, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
