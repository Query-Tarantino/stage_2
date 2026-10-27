package org.ulpgc.tarantino.bench.load;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.bench.run.RunContext;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchLoadGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchLoadGenerator.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final String baseUrl;
    private final List<String> queries;
    private final RunContext runContext;

    public SearchLoadGenerator(String baseUrl, List<String> queries, RunContext runContext) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.queries = List.copyOf(queries);
        this.runContext = runContext;
    }

    public static List<String> loadQueries(Path queriesFile) throws IOException {
        return Files.readAllLines(queriesFile).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(SearchLoadGenerator::extractQuery)
                .filter(query -> !query.isEmpty())
                .toList();
    }

    private static String extractQuery(String line) {
        int colonIndex = line.indexOf(':');
        return colonIndex >= 0 ? line.substring(colonIndex + 1).trim() : line.trim();
    }

    public void run(int concurrency, int totalRequests) throws IOException, InterruptedException {
        run(concurrency, totalRequests, null);
    }

    public void run(int concurrency, int totalRequests, String configName) throws IOException, InterruptedException {
        Path loadDir = runContext.loadDirectory();
        Path aggregateFile = loadDir.resolve("requests.csv");
        boolean aggregateExists = Files.exists(aggregateFile);
        Path configFile = null;
        if (configName != null && !configName.isBlank()) {
            String sanitizedConfig = configName.replaceAll("[^a-zA-Z0-9_=-]", "_");
            configFile = loadDir.resolve(sanitizedConfig + ".csv");
        }

        LOGGER.info("Starting load generator: {} requests across {} threads", totalRequests, concurrency);

        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch latch = new CountDownLatch(totalRequests);

        try (BufferedWriter aggregateWriter = Files.newBufferedWriter(
                aggregateFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
             BufferedWriter configWriter = configFile != null ? Files.newBufferedWriter(
                     configFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING) : null) {

            if (!aggregateExists) {
                aggregateWriter.write("instant,path,status,latency_us\n");
            }
            if (configWriter != null) {
                configWriter.write("instant,path,status,latency_us\n");
            }
            Object writeLock = new Object();

            for (int requestIndex = 0; requestIndex < totalRequests; requestIndex++) {
                final String query = queries.get(requestIndex % queries.size());
                executor.submit(() -> {
                    try {
                        executeRequest(client, query, aggregateWriter, configWriter, writeLock);
                    } catch (Exception exception) {
                        LOGGER.error("Failed request: {}", exception.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        } finally {
            executor.shutdown();
        }
        LOGGER.info("Load generator completed: {} requests recorded", totalRequests);
    }

    private void executeRequest(
            HttpClient client,
            String query,
            BufferedWriter aggregateWriter,
            BufferedWriter configWriter,
            Object writeLock) throws IOException, InterruptedException {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String relativePath = "/search?q=" + encoded;
        URI uri = URI.create(baseUrl + relativePath);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();

        Instant instant = Instant.now();
        long start = System.nanoTime();
        int status;
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            status = response.statusCode();
        } catch (Exception exception) {
            status = 500;
        }
        long latencyUs = (System.nanoTime() - start) / 1000;

        String line = instant + "," + relativePath + "," + status + "," + latencyUs + "\n";
        synchronized (writeLock) {
            aggregateWriter.write(line);
            if (configWriter != null) {
                configWriter.write(line);
            }
        }
    }

    public static void main(String[] args) {
        try {
            String baseUrl = args.length > 0 ? args[0] : System.getenv().getOrDefault("TARANTINO_FRONTEND_URL", "http://localhost:8080");
            Path runDir = args.length > 1 ? Paths.get(args[1]) : Paths.get("benchmarks/runs/latest");
            int concurrency = args.length > 2 ? Integer.parseInt(args[2]) : Integer.parseInt(System.getenv().getOrDefault("TARANTINO_CONCURRENCY", "4"));
            int requests = args.length > 3 ? Integer.parseInt(args[3]) : Integer.parseInt(System.getenv().getOrDefault("TARANTINO_BENCH_REQUESTS", "100"));
            String configName = args.length > 4 ? args[4] : null;

            Path queriesFile = findQueriesFile();
            List<String> queries = loadQueries(queriesFile);
            RunContext context = RunContext.fromPath(runDir);
            SearchLoadGenerator generator = new SearchLoadGenerator(baseUrl, queries, context);
            generator.run(concurrency, requests, configName);
        } catch (Exception exception) {
            LOGGER.error("Load generator failed: {}", exception.getMessage(), exception);
            System.exit(1);
        }
    }

    private static Path findQueriesFile() {
        List<Path> candidates = List.of(
                Paths.get("workload/queries.txt"),
                Paths.get("../workload/queries.txt"),
                Paths.get("../../workload/queries.txt")
        );
        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find workload/queries.txt"));
    }
}
