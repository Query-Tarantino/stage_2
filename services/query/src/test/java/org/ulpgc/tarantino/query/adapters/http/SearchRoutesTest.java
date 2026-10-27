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
import org.ulpgc.tarantino.query.model.BookDetails;
import org.ulpgc.tarantino.query.model.SearchResult;
import org.ulpgc.tarantino.query.model.Snapshot.BookFilter;
import org.ulpgc.tarantino.query.model.TermPostings;
import org.ulpgc.tarantino.query.ports.MetadataReader;
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
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchRoutesTest {
    private static final String BATCH = "6716f0c2a1b2c3d4e5f60718";
    private static final List<TermPostings> POSTINGS = List.of(
            new TermPostings("pride", List.of(84, 1342), List.of(6, 55)),
            new TermPostings("prejudice", List.of(1342), List.of(11)));
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
    private static final MetadataReader FAKE_METADATA = new MetadataReader() {
        @Override
        public List<SearchResult> results(int generation, Collection<Integer> bookIds) {
            return List.of(new SearchResult(1342, "Pride and Prejudice", "Jane Austen", "en", 1998));
        }

        @Override
        public Optional<BookDetails> bookDetails(int generation, int bookId) {
            if (bookId == 1342) {
                return Optional.of(new BookDetails(
                        1342, "Pride and Prejudice", "Jane Austen", "en", List.of("en"), 1998, null));
            }
            return Optional.empty();
        }

        @Override
        public Map<Integer, BookFilter> filters(int generation, Collection<Integer> bookIds) {
            return Map.of(
                    1342, new BookFilter(List.of("en"), 1998),
                    84, new BookFilter(List.of("en"), 1818));
        }

        @Override
        public Set<Integer> matchingBookIds(
                int generation, Collection<String> authorTerms, String language, Integer year) {
            return Set.of(1342);
        }
    };

    private final HttpClient client = HttpClient.newHttpClient();
    private final SnapshotRefresher snapshots = new SnapshotRefresher(ONE_BATCH, FAKE_METADATA);
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
    void answersSearchWithFilters() throws IOException, InterruptedException {
        start();
        snapshots.refresh();
        HttpResponse<String> response = get("/search?q=pride&author=Austen&language=en&year=1998");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"filters\":{\"author\":\"Austen\",\"language\":\"en\",\"year\":1998}"));
    }

    @Test
    void answersBookDetailsWhenFoundInSnapshot() throws IOException, InterruptedException {
        start();
        snapshots.refresh();
        HttpResponse<String> response = get("/books/1342");
        assertEquals(200, response.statusCode());
        assertEquals("{\"book_id\":1342,\"title\":\"Pride and Prejudice\",\"author\":\"Jane Austen\",\"language\":\"en\","
                + "\"languages\":[\"en\"],\"year\":1998,\"original_year\":null}", response.body());
    }

    @Test
    void answersNotFoundWhenBookIsNotInSnapshot() throws IOException, InterruptedException {
        start();
        snapshots.refresh();
        HttpResponse<String> response = get("/books/999");
        assertEquals(404, response.statusCode());
    }

    @Test
    void rejectsInvalidBookId() throws IOException, InterruptedException {
        start();
        snapshots.refresh();
        assertEquals(400, get("/books/0").statusCode());
        assertEquals(400, get("/books/-5").statusCode());
        assertEquals(400, get("/books/abc").statusCode());
        assertEquals(400, get("/books/99999999999999999999").statusCode());
    }

    @Test
    void answersFilterSummary() throws IOException, InterruptedException {
        start();
        snapshots.refresh();
        HttpResponse<String> response = get("/search/filters");
        assertEquals(200, response.statusCode());
        assertEquals("{\"languages\":[{\"code\":\"en\",\"name\":\"English\",\"books\":2}],\"years\":{\"min\":1818,\"max\":1998},\"total_books\":2}",
                response.body());
    }

    @Test
    void validatesTheParametersBeforeLookingAtTheSnapshot() throws IOException, InterruptedException {
        start();
        assertEquals(400, get("/search?q=").statusCode());
        assertEquals(400, get("/search?q=pride&limit=101").statusCode());
    }

    @Test
    void setsCorsHeaderFromConfig() throws IOException, InterruptedException {
        start();
        snapshots.refresh();
        HttpResponse<String> response = get("/search?q=pride");
        assertEquals(200, response.statusCode());
        assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
    }

    private void start() throws IOException {
        Tokenizer tokenizer = new Tokenizer(Set.of());
        SearchCommand search = new SearchCommand(
                (generation, terms, batchIds) -> POSTINGS.stream()
                        .filter(posting -> terms.contains(posting.term()))
                        .toList(),
                FAKE_METADATA,
                tokenizer);
        TarantinoConfig config = TarantinoConfig.from(Map.of(
                "TARANTINO_PORT", String.valueOf(freePort()),
                "TARANTINO_CORS_ORIGIN", "*"), TarantinoService.QUERY);
        app = TarantinoServer.start(
                config, List.of(), new SearchRoutes(snapshots, search, FAKE_METADATA, tokenizer, "*")::register);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}