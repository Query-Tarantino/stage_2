package org.ulpgc.tarantino.query.adapters.http;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.http.TarantinoServer;
import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.query.commands.SearchCommand;
import org.ulpgc.tarantino.query.commands.SnapshotRefresher;
import org.ulpgc.tarantino.query.model.SearchResult;
import org.ulpgc.tarantino.query.model.TermPostings;
import org.ulpgc.tarantino.query.ports.PublishedBatches;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchRoutesTest {

    private static final String BATCH = "6716f0c2a1b2c3d4e5f60718";
    private static final PublishedBatches ONE_BATCH = new PublishedBatches() {
        @Override
        public int currentGeneration() {
            return 1;
        }

        @Override
        public List<String> publishedBatchIds(int generation) {
            return List.of(BATCH);
        }

        @Override
        public Map<String, List<Integer>> bookIds(int generation, Collection<String> batchIds) {
            return Map.of(BATCH, List.of(1342, 84));
        }
    };

    private final HttpClient client = HttpClient.newHttpClient();
    private final SnapshotRefresher snapshots = new SnapshotRefresher(ONE_BATCH);
    private Javalin app;

    @AfterEach
    void stop() {
        app.stop();
        client.close();
    }

    @Test
    void answersUnavailableUntilThereIsASnapshot() throws IOException, InterruptedException {
        start();

        HttpResponse<String> response = get("/search?q=pride");

        assertEquals(503, response.statusCode());
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").orElseThrow());
    }

    @Test
    void answersTheSearchOfTheSnapshotInSnakeCase() throws IOException, InterruptedException {
        start();
        snapshots.refresh();

        HttpResponse<String> response = get("/search?q=pride%20prejudice");

        assertEquals(200, response.statusCode());
        assertEquals("{\"query\":\"pride prejudice\",\"filters\":{},\"total\":1,\"offset\":0,\"limit\":20,\"results\":"
                + "[{\"book_id\":1342,\"title\":\"Pride and Prejudice\",\"author\":\"Jane Austen\",\"language\":\"en\",\"year\":1998}]}",
                response.body());
    }

    @Test
    void validatesTheParametersBeforeLookingAtTheSnapshot() throws IOException, InterruptedException {
        start();

        assertEquals(400, get("/search?q=").statusCode());
        assertEquals(400, get("/search?q=pride&limit=101").statusCode());
    }

    private void start() throws IOException {
        SearchCommand search = new SearchCommand(
                (generation, terms, batchIds) -> List.of(new TermPostings("pride", List.of(84, 1342), List.of(6, 55)),
                        new TermPostings("prejudice", List.of(1342), List.of(11))),
                (generation, bookIds) -> List.of(new SearchResult(1342, "Pride and Prejudice", "Jane Austen", "en", 1998)),
                new Tokenizer(Set.of()));
        TarantinoConfig config = TarantinoConfig.from(Map.of("TARANTINO_PORT", String.valueOf(freePort())), TarantinoService.QUERY);
        app = TarantinoServer.start(config, List.of(), new SearchRoutes(snapshots, search)::register);
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
