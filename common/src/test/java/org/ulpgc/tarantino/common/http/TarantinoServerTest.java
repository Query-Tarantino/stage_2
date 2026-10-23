package org.ulpgc.tarantino.common.http;

import io.javalin.Javalin;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.model.BookTask;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TarantinoServerTest {

    private static final Consumer<Javalin> NO_ROUTES = app -> {
    };

    private final HttpClient client = HttpClient.newHttpClient();
    private Javalin app;

    @AfterEach
    void stop() {
        app.stop();
        client.close();
    }

    @Test
    void listsTheDependenciesThatDoNotAnswerInTheirOrder() throws IOException, InterruptedException {
        app = TarantinoServer.start(config(Map.of()), List.of(new Dependency(Health.MONGO, () -> false),
                new Dependency(Health.HAZELCAST, () -> true), new Dependency(Health.SNAPSHOT, () -> false)), NO_ROUTES);

        HttpResponse<String> response = get("/health");

        assertEquals(503, response.statusCode());
        assertEquals("{\"status\":\"DOWN\",\"down\":[\"mongo\",\"snapshot\"]}", response.body());
    }

    @Test
    void answersInSnakeCaseWithTheSharedMapper() throws IOException, InterruptedException {
        BookTask task = new BookTask(1342, Instant.parse("2026-10-21T10:05:00Z"));
        app = TarantinoServer.start(config(Map.of()), List.of(), routes -> routes.get("/task", context -> context.json(task)));

        assertEquals("{\"book_id\":1342,\"enqueued_at\":\"2026-10-21T10:05:00Z\"}", get("/task").body());
    }

    @Test
    void boundsTheRequestThreadsWithTarantinoHttpThreads() throws IOException {
        app = TarantinoServer.start(config(Map.of("TARANTINO_HTTP_THREADS", "16")), List.of(), NO_ROUTES);

        assertEquals(16, ((QueuedThreadPool) app.jettyServer().server().getThreadPool()).getMaxThreads());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static TarantinoConfig config(Map<String, String> variables) throws IOException {
        Map<String, String> environment = new HashMap<>(variables);
        environment.put("TARANTINO_PORT", String.valueOf(freePort()));
        return TarantinoConfig.from(environment, TarantinoService.QUERY);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
