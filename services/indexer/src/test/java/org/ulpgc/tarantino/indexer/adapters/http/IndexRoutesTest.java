package org.ulpgc.tarantino.indexer.adapters.http;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.http.TarantinoServer;
import org.ulpgc.tarantino.common.model.RebuildStatus;
import org.ulpgc.tarantino.indexer.model.rebuild.IndexStatus;
import org.ulpgc.tarantino.indexer.model.rebuild.Rebuild;
import org.ulpgc.tarantino.indexer.model.rebuild.RebuildProgress;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndexRoutesTest {

    private static final Rebuild REBUILD = new Rebuild(1, 2, RebuildStatus.RUNNING);
    private static final IndexStatus STATUS = new IndexStatus(1, 2800, 28, Instant.parse("2026-10-21T10:06:00Z"), 512.4,
            new RebuildProgress(2, RebuildStatus.RUNNING, 1200));

    private final HttpClient client = HttpClient.newHttpClient();
    private Javalin app;

    @AfterEach
    void stop() {
        app.stop();
        client.close();
    }

    @Test
    void acceptsARebuildAndPointsToTheIndexStatus() throws IOException, InterruptedException {
        start(new IndexRoutes(() -> REBUILD, () -> STATUS));

        HttpResponse<String> response = post("/index/rebuild");

        assertEquals(202, response.statusCode());
        assertEquals("/index/status", response.headers().firstValue("Location").orElseThrow());
        assertEquals("{\"generation\":1,\"target_generation\":2,\"status\":\"RUNNING\"}", response.body());
    }

    @Test
    void answersTheStatusOfTheCurrentGenerationInSnakeCase() throws IOException, InterruptedException {
        start(new IndexRoutes(() -> REBUILD, () -> STATUS));

        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + app.port()
                + "/index/status")).build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("{\"generation\":1,\"books_indexed\":2800,\"batches_published\":28,"
                + "\"last_update\":\"2026-10-21T10:06:00Z\",\"index_size_mb\":512.4,"
                + "\"rebuild\":{\"target_generation\":2,\"status\":\"RUNNING\",\"books_pending\":1200}}", response.body());
    }

    private void start(IndexRoutes routes) throws IOException {
        TarantinoConfig config = TarantinoConfig.from(Map.of("TARANTINO_PORT", String.valueOf(freePort())), TarantinoService.INDEXER);
        app = TarantinoServer.start(config, List.of(), routes::register);
    }

    private HttpResponse<String> post(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
