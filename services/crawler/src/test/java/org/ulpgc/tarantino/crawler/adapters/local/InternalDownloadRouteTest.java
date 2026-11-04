package org.ulpgc.tarantino.crawler.adapters.local;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.http.InternalTasks;
import org.ulpgc.tarantino.common.http.TarantinoServer;
import org.ulpgc.tarantino.common.model.BookTask;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalDownloadRouteTest {

    private static final String TASK = "{\"book_id\": 1342, \"enqueued_at\": \"2026-10-21T10:05:00Z\"}";

    private final HttpClient client = HttpClient.newHttpClient();
    private final LocalDownloadQueue localDownloads = new LocalDownloadQueue(1);
    private Javalin app;

    @BeforeEach
    void start() throws IOException {
        TarantinoConfig config = TarantinoConfig.from(Map.of("TARANTINO_PORT", String.valueOf(freePort())), TarantinoService.CRAWLER);
        app = TarantinoServer.start(config, List.of(), server -> InternalTasks.register(server, InternalTasks.DOWNLOAD, localDownloads::offer));
    }

    @AfterEach
    void stop() {
        app.stop();
        client.close();
    }

    @Test
    void acceptsABookIntoTheLocalQueue() throws Exception {
        assertEquals(202, post(TASK).statusCode());

        assertEquals(Optional.of(new BookTask(1342, Instant.parse("2026-10-21T10:05:00Z"))), localDownloads.next(Duration.ofMillis(10)));
    }

    @Test
    void rejectsAnInvalidBodyOrBookId() throws Exception {
        HttpResponse<String> notJson = post("pride");
        HttpResponse<String> zero = post("{\"book_id\": 0, \"enqueued_at\": \"2026-10-21T10:05:00Z\"}");

        assertEquals(400, notJson.statusCode());
        assertEquals(400, zero.statusCode());
        assertTrue(zero.body().contains("\"code\":\"INVALID_PARAMETER\""), zero.body());
        assertTrue(zero.body().contains("\"parameter\":\"book_id\""), zero.body());
    }

    @Test
    void answersUnavailableWithARetryAfterOfOneSecondWhenTheQueueIsFull() throws Exception {
        post(TASK);

        HttpResponse<String> full = post(TASK);

        assertEquals(503, full.statusCode());
        assertEquals(Optional.of("1"), full.headers().firstValue("Retry-After"));
        assertTrue(full.body().contains("\"code\":\"DEPENDENCY_UNAVAILABLE\""), full.body());
    }

    private HttpResponse<String> post(String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + InternalTasks.DOWNLOAD))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
