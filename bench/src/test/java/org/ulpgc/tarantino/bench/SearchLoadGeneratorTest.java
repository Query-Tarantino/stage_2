package org.ulpgc.tarantino.bench;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.bench.load.SearchLoadGenerator;
import org.ulpgc.tarantino.bench.run.RunContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchLoadGeneratorTest {

    private static Javalin app;
    private static int port;

    @BeforeAll
    static void startServer() throws Exception {
        app = Javalin.create().start(0);
        port = app.port();
        app.get("/search", ctx -> ctx.result("{\"total\": 1}"));
        HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/search?q=warmup")).build(),
                HttpResponse.BodyHandlers.discarding());
    }

    @AfterAll
    static void stopServer() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    void shouldCreateRunDirectoriesAndWriteRequestsCsv(@TempDir Path tempDir) throws IOException, InterruptedException {
        RunContext context = RunContext.of("test-bench", "abcdef1", tempDir);
        assertTrue(Files.exists(context.loadDirectory()));
        assertTrue(Files.exists(context.jmhDirectory()));
        assertTrue(Files.exists(context.explainDirectory()));

        List<String> queries = List.of("love", "night", "pride prejudice");
        SearchLoadGenerator generator = new SearchLoadGenerator("http://localhost:" + port, queries, context);
        generator.run(2, Duration.ofMillis(20), Duration.ofMillis(150));

        Path csvPath = context.loadDirectory().resolve("requests.csv");
        assertTrue(Files.exists(csvPath));
        List<String> lines = Files.readAllLines(csvPath);
        assertTrue(lines.size() > 1);
        assertEquals("instant,path,status,latency_us", lines.getFirst());
        assertTrue(lines.get(1).contains(",/search?q="));
        assertTrue(lines.get(1).contains(",200,"));
    }

    @Test
    void shouldPreserveConfigurationCsv(@TempDir Path tempDir) throws IOException, InterruptedException {
        RunContext context = RunContext.of("test-bench", "abcdef1", tempDir);
        List<String> queries = List.of("love", "night");
        SearchLoadGenerator generator = new SearchLoadGenerator("http://localhost:" + port, queries, context);
        generator.run(2, Duration.ofMillis(20), Duration.ofMillis(150), "clients_4");

        Path configCsv = context.loadDirectory().resolve("clients_4.csv");
        Path aggregateCsv = context.loadDirectory().resolve("requests.csv");
        assertTrue(Files.exists(configCsv));
        assertTrue(Files.exists(aggregateCsv));
        assertTrue(Files.readAllLines(configCsv).size() > 1);
        assertTrue(Files.readAllLines(aggregateCsv).size() > 1);
    }

    @Test
    void shouldRunClosedLoopWithDurations(@TempDir Path tempDir) throws IOException, InterruptedException {
        RunContext context = RunContext.of("test-bench", "abcdef1", tempDir);
        List<String> queries = List.of("love", "night");
        SearchLoadGenerator generator = new SearchLoadGenerator("http://localhost:" + port, queries, context);
        generator.run(2, Duration.ofMillis(50), Duration.ofMillis(200), "clients_2");

        Path configCsv = context.loadDirectory().resolve("clients_2.csv");
        Path aggregateCsv = context.loadDirectory().resolve("requests.csv");
        assertTrue(Files.exists(configCsv));
        assertTrue(Files.exists(aggregateCsv));
        assertTrue(Files.readAllLines(configCsv).size() > 1);
        assertTrue(Files.readAllLines(aggregateCsv).size() > 1);
    }

    @Test
    void shouldExtractQueriesFromLines() {
        List<String> queries = List.of("frequent: love", "rare: pyromaniac", "simple");
        assertEquals("love", queries.stream().map(query -> query.substring(query.indexOf(':') + 1).trim()).toList().getFirst());
    }
}
