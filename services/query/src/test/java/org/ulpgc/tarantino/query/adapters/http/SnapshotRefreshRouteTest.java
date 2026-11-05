package org.ulpgc.tarantino.query.adapters.http;

import io.javalin.Javalin;
import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.http.TarantinoServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnapshotRefreshRouteTest {

    @Test
    void asksTheSnapshotThreadForARefreshAndAnswersAccepted() throws Exception {
        AtomicInteger refreshes = new AtomicInteger();
        TarantinoConfig config = TarantinoConfig.from(Map.of("TARANTINO_PORT", String.valueOf(freePort())), TarantinoService.QUERY);
        Javalin app = TarantinoServer.start(config, List.of(), new SnapshotRefreshRoute(refreshes::incrementAndGet)::register);
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + app.port()
                    + SnapshotRefreshRoute.PATH)).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());

            assertEquals(202, response.statusCode());
            assertEquals(1, refreshes.get());
        } finally {
            app.stop();
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
