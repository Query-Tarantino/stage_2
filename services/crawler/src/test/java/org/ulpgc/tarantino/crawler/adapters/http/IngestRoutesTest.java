package org.ulpgc.tarantino.crawler.adapters.http;

import com.hazelcast.client.HazelcastClientOfflineException;
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
import org.ulpgc.tarantino.common.model.BookErrorCode;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.crawler.ports.IngestRequests;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestRoutesTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-10-21T10:05:00Z");
    private static final Instant NOW = Instant.parse("2026-10-21T10:06:00Z");

    private final HttpClient client = HttpClient.newHttpClient();
    private final Map<Integer, BookState> states = new HashMap<>();
    private final List<BookTask> enqueued = new ArrayList<>();
    private boolean hazelcastDown;
    private Javalin app;

    @BeforeEach
    void start() throws IOException {
        TarantinoConfig config = TarantinoConfig.from(Map.of("TARANTINO_PORT", String.valueOf(freePort())), TarantinoService.CRAWLER);
        IngestRoutes routes = new IngestRoutes(bookId -> Optional.ofNullable(states.get(bookId)), new FakeRequests(),
                () -> List.of(5, 17, 1342), this::enqueue, new TransitionLog(), Clock.fixed(NOW, ZoneOffset.UTC));
        app = TarantinoServer.start(config, List.of(), routes::register);
    }

    @AfterEach
    void stop() {
        app.stop();
        client.close();
    }

    @Test
    void createsTheStateOfANewBookAndEnqueuesIt() throws IOException, InterruptedException {
        HttpResponse<String> response = send("POST", "/ingest/1342");

        assertEquals(202, response.statusCode());
        assertEquals("/ingest/status/1342", response.headers().firstValue("Location").orElseThrow());
        assertTrue(response.body().contains("\"status\":\"QUEUED\""));
        assertEquals(List.of(new BookTask(1342, NOW)), enqueued);
    }

    @Test
    void retriesAFailedBookWithNoAttemptsAndEnqueuesIt() throws IOException, InterruptedException {
        states.put(1342, new BookState(1342, BookStatus.FAILED, 3, BookErrorCode.NETWORK_ERROR, UPDATED_AT));

        HttpResponse<String> response = send("POST", "/ingest/1342");

        assertEquals(202, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"QUEUED\",\"attempts\":0,\"last_error\":null"));
        assertEquals(List.of(new BookTask(1342, NOW)), enqueued);
    }

    @ParameterizedTest
    @EnumSource(value = BookStatus.class, names = {"QUEUED", "DOWNLOADING"})
    void acceptsABookOnItsWayWithoutEnqueuingItAgain(BookStatus status) throws IOException, InterruptedException {
        states.put(1342, new BookState(1342, status, 1, null, UPDATED_AT));

        HttpResponse<String> response = send("POST", "/ingest/1342");

        assertEquals(202, response.statusCode());
        assertEquals("/ingest/status/1342", response.headers().firstValue("Location").orElseThrow());
        assertTrue(response.body().contains("\"status\":\"" + status + "\""));
        assertEquals(List.of(), enqueued);
    }

    @ParameterizedTest
    @EnumSource(value = BookStatus.class, names = {"DOWNLOADED", "INDEXING", "PUBLISHED"})
    void answersOkWithoutLocationForADownloadedBook(BookStatus status) throws IOException, InterruptedException {
        states.put(1342, new BookState(1342, status, 1, null, UPDATED_AT));

        HttpResponse<String> response = send("POST", "/ingest/1342");

        assertEquals(200, response.statusCode());
        assertFalse(response.headers().firstValue("Location").isPresent());
        assertEquals(List.of(), enqueued);
    }

    @Test
    void answersUnavailableWhenHazelcastFailsAfterTheStateIsWritten() throws IOException, InterruptedException {
        hazelcastDown = true;

        HttpResponse<String> response = send("POST", "/ingest/1342");

        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("\"code\":\"DEPENDENCY_UNAVAILABLE\""));
        assertEquals(BookStatus.QUEUED, states.get(1342).status());
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
    void listsTheDownloadedBooksWithTheirCount() throws IOException, InterruptedException {
        HttpResponse<String> response = send("GET", "/ingest/list");

        assertEquals(200, response.statusCode());
        assertEquals("{\"count\":3,\"books\":[5,17,1342]}", response.body());
    }

    @ParameterizedTest
    @EnumSource(value = Method.class)
    void rejectsAnIdThatIsNotAPositiveInt32(Method method) throws IOException, InterruptedException {
        HttpResponse<String> response = send(method.name(), method.path + "01");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"code\":\"INVALID_PARAMETER\""));
        assertTrue(response.body().contains("\"parameter\":\"book_id\""));
        assertEquals(Map.of(), states);
    }

    private void enqueue(BookTask task) {
        if (hazelcastDown) {
            throw new HazelcastClientOfflineException();
        }
        enqueued.add(task);
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

    private enum Method {
        GET("/ingest/status/"),
        POST("/ingest/");

        private final String path;

        Method(String path) {
            this.path = path;
        }
    }

    private final class FakeRequests implements IngestRequests {

        @Override
        public boolean create(int bookId) {
            return states.putIfAbsent(bookId, new BookState(bookId, BookStatus.QUEUED, 0, null, NOW)) == null;
        }

        @Override
        public boolean retry(int bookId) {
            if (states.get(bookId).status() != BookStatus.FAILED) {
                return false;
            }
            states.put(bookId, new BookState(bookId, BookStatus.QUEUED, 0, null, NOW));
            return true;
        }
    }
}
