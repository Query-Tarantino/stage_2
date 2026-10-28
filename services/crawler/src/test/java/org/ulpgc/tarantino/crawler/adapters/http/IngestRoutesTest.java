package org.ulpgc.tarantino.crawler.adapters.http;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.http.TarantinoServer;
import org.ulpgc.tarantino.common.model.BookState;
import org.ulpgc.tarantino.common.model.BookStatus;

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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestRoutesTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-10-21T10:05:00Z");

    private final HttpClient client = HttpClient.newHttpClient();
    private final Map<Integer, BookState> states = new HashMap<>();
    private Javalin app;

    @BeforeEach
    void start() throws IOException {
        TarantinoConfig config = TarantinoConfig.from(Map.of("TARANTINO_PORT", String.valueOf(freePort())), TarantinoService.CRAWLER);
        app = TarantinoServer.start(config, List.of(), new IngestRoutes(bookId -> Optional.ofNullable(states.get(bookId)))::register);
    }

    @AfterEach
    void stop() {
        app.stop();
        client.close();
    }

    @Test
    void answersTheStateOfABook() throws IOException, InterruptedException {
        states.put(1342, new BookState(1342, BookStatus.DOWNLOADING, 1, null, UPDATED_AT));

        HttpResponse<String> response = send("GET", "/ingest/status/1342");

        assertEquals(200, response.statusCode());
        assertEquals("{\"book_id\":1342,\"status\":\"DOWNLOADING\",\"attempts\":1,\"last_error\":null,"
                + "\"updated_at\":\"2026-10-21T10:05:00Z\"}", response.body());
    }

    @Test
    void answersNotFoundForABookWithoutState() throws IOException, InterruptedException {
        HttpResponse<String> response = send("GET", "/ingest/status/84");

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("\"code\":\"BOOK_NOT_FOUND\""));
    }

    @Test
    void rejectsAnIdThatIsNotAPositiveInt32() throws IOException, InterruptedException {
        HttpResponse<String> response = send("GET", "/ingest/status/01");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"code\":\"INVALID_PARAMETER\""));
        assertTrue(response.body().contains("\"parameter\":\"book_id\""));
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
