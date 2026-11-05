package org.ulpgc.tarantino.control.adapters.http;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.http.TarantinoServer;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.control.commands.ControlStatusCommand;
import org.ulpgc.tarantino.control.ports.QueueSizes;
import org.ulpgc.tarantino.control.ports.StatusCounts;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlStatusRoutesTest {

    private static final StatusCounts SOME_BOOKS = new StatusCounts() {
        @Override
        public int currentGeneration() {
            return 1;
        }

        @Override
        public Map<BookStatus, Long> booksByStatus() {
            return Map.of(BookStatus.QUEUED, 10L, BookStatus.PUBLISHED, 2680L);
        }
    };
    private static final QueueSizes TEN_TO_DOWNLOAD = new QueueSizes() {
        @Override
        public int downloads() {
            return 10;
        }

        @Override
        public int index() {
            return 0;
        }
    };

    private final HttpClient client = HttpClient.newHttpClient();
    private Javalin app;

    @AfterEach
    void stop() {
        app.stop();
        client.close();
    }

    @Test
    void answersEveryStateAndBothQueuesInSnakeCase() throws IOException, InterruptedException {
        start(Optional.of(Instant.parse("2026-10-21T10:06:30Z")));

        HttpResponse<String> response = get("/control/status");

        assertEquals(200, response.statusCode());
        assertEquals("{\"generation\":1,\"books\":{\"QUEUED\":10,\"DOWNLOADING\":0,\"DOWNLOADED\":0,\"INDEXING\":0,"
                + "\"PUBLISHED\":2680,\"FAILED\":0},\"queues\":{\"books-to-download\":10,\"books-to-index\":0},"
                + "\"last_reconciliation\":\"2026-10-21T10:06:30Z\"}", response.body());
    }

    @Test
    void answersNullBeforeTheFirstReconciliation() throws IOException, InterruptedException {
        start(Optional.empty());

        assertTrue(get("/control/status").body().endsWith("\"last_reconciliation\":null}"));
    }

    private void start(Optional<Instant> lastReconciliation) throws IOException {
        ControlStatusCommand status = new ControlStatusCommand(SOME_BOOKS, TEN_TO_DOWNLOAD, () -> lastReconciliation);
        TarantinoConfig config = TarantinoConfig.from(Map.of("TARANTINO_PORT", String.valueOf(freePort())), TarantinoService.CONTROL);
        app = TarantinoServer.start(config, List.of(), new ControlStatusRoutes(status)::register);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
