package org.ulpgc.tarantino.indexer.adapters.http;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.http.TarantinoServer;
import org.ulpgc.tarantino.common.model.BookState;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookTask;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookRoutesTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-10-21T10:05:00Z");
    private static final Instant NOW = Instant.parse("2026-10-21T10:06:00Z");

    private final HttpClient client = HttpClient.newHttpClient();
    private final Map<Integer, BookState> states = new HashMap<>();
    private final List<BookTask> enqueued = new ArrayList<>();
    private Javalin app;

    @BeforeEach
    void start() throws IOException {
        TarantinoConfig config = TarantinoConfig.from(Map.of("TARANTINO_PORT", String.valueOf(freePort())), TarantinoService.INDEXER);
        app = TarantinoServer.start(config, List.of(), new BookRoutes(bookId -> Optional.ofNullable(states.get(bookId)), enqueued::add,
                Clock.fixed(NOW, ZoneOffset.UTC))::register);
    }

    @AfterEach
    void stop() {
        app.stop();
        client.close();
    }

    @Test
    void answersTheStateOfABook() throws IOException, InterruptedException {
        book(1342, BookStatus.DOWNLOADED);

        HttpResponse<String> response = send("GET", "/index/status/1342");

        assertEquals(200, response.statusCode());
        assertEquals("{\"book_id\":1342,\"status\":\"DOWNLOADED\",\"attempts\":1,\"last_error\":null,"
                + "\"updated_at\":\"2026-10-21T10:05:00Z\"}", response.body());
    }

    @Test
    void answersNotFoundForABookWithoutState() throws IOException, InterruptedException {
        HttpResponse<String> response = send("GET", "/index/status/84");

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("\"code\":\"BOOK_NOT_FOUND\""));
        assertEquals(404, send("POST", "/index/update/84").statusCode());
    }

    @Test
    void enqueuesADownloadedBookAndPointsToItsState() throws IOException, InterruptedException {
        book(1342, BookStatus.DOWNLOADED);

        HttpResponse<String> response = send("POST", "/index/update/1342");

        assertEquals(202, response.statusCode());
        assertEquals("/index/status/1342", response.headers().firstValue("Location").orElseThrow());
        assertTrue(response.body().contains("\"status\":\"DOWNLOADED\""));
        assertEquals(List.of(new BookTask(1342, NOW)), enqueued);
    }

    @Test
    void acceptsABookAlreadyInABatchWithoutEnqueuingItAgain() throws IOException, InterruptedException {
        book(1342, BookStatus.INDEXING);

        HttpResponse<String> response = send("POST", "/index/update/1342");

        assertEquals(202, response.statusCode());
        assertEquals("/index/status/1342", response.headers().firstValue("Location").orElseThrow());
        assertEquals(List.of(), enqueued);
    }

    @Test
    void answersAPublishedBookWithItsStateAndNoLocation() throws IOException, InterruptedException {
        book(1342, BookStatus.PUBLISHED);

        HttpResponse<String> response = send("POST", "/index/update/1342");

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Location").isEmpty());
        assertEquals(List.of(), enqueued);
    }

    @ParameterizedTest
    @EnumSource(value = BookStatus.class, names = {"QUEUED", "DOWNLOADING", "FAILED"})
    void refusesToIndexABookThatIsNotDownloaded(BookStatus status) throws IOException, InterruptedException {
        book(1342, status);

        HttpResponse<String> response = send("POST", "/index/update/1342");

        assertEquals(409, response.statusCode());
        assertTrue(response.body().contains("\"code\":\"NOT_DOWNLOADED\""));
        assertEquals(List.of(), enqueued);
    }

    @Test
    void rejectsAnIdThatIsNotABookId() throws IOException, InterruptedException {
        HttpResponse<String> response = send("GET", "/index/status/01");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"parameter\":\"book_id\""));
    }

    private void book(int bookId, BookStatus status) {
        states.put(bookId, new BookState(bookId, status, 1, null, UPDATED_AT));
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
