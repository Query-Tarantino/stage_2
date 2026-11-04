package org.ulpgc.tarantino.bench.load;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.bench.run.RunContext;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
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
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

public class SearchLoadGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchLoadGenerator.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    public static final Duration DEFAULT_WARMUP = Duration.ofSeconds(30);
    public static final Duration DEFAULT_MEASUREMENT = Duration.ofSeconds(120);
    private static final String WARMUP_VARIABLE = "TARANTINO_WARMUP_SECONDS";
    private static final String MEASUREMENT_VARIABLE = "TARANTINO_MEASUREMENT_SECONDS";
    private static final String USAGE = "Usage: bench load <front-end url> <run directory> <clients> <trial>";
    private static final int ARGUMENTS = 4;
    private static final int FAILED = 1;
    private static final int WRONG_USAGE = 2;

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

    public void run(int concurrency) throws IOException, InterruptedException {
        run(concurrency, DEFAULT_WARMUP, DEFAULT_MEASUREMENT, null);
    }

    public void run(int concurrency, String configName) throws IOException, InterruptedException {
        run(concurrency, DEFAULT_WARMUP, DEFAULT_MEASUREMENT, configName);
    }

    public void run(int concurrency, Duration warmupDuration, Duration measurementDuration) throws IOException, InterruptedException {
        run(concurrency, warmupDuration, measurementDuration, null);
    }

    public void run(int concurrency, Duration warmupDuration, Duration measurementDuration, String configName)
            throws IOException, InterruptedException {
        Path loadDir = runContext.loadDirectory();
        String sanitizedConfig = (configName != null && !configName.isBlank())
                ? configName.replaceAll("[^a-zA-Z0-9_=-]", "_")
                : "default";
        Path trialFile = loadDir.resolve(sanitizedConfig + ".csv.gz");

        LOGGER.info("Starting closed loop load generator: {} clients, warmup={}, measurement={}",
                concurrency, warmupDuration, measurementDuration);

        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        AtomicInteger queryIndex = new AtomicInteger(0);
        AtomicBoolean recording = new AtomicBoolean(false);
        AtomicBoolean stop = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(concurrency);
        Object writeLock = new Object();

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new GZIPOutputStream(Files.newOutputStream(trialFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)),
                StandardCharsets.UTF_8))) {

            writer.write("instant,path,status,latency_us\n");

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int clientIndex = 0; clientIndex < concurrency; clientIndex++) {
                    executor.submit(() -> {
                        try {
                            while (!stop.get()) {
                                int nextQuery = queryIndex.getAndIncrement();
                                String query = queries.get(Math.floorMod(nextQuery, queries.size()));
                                executeRequest(client, query, writer, recording, writeLock);
                            }
                        } catch (Exception exception) {
                            LOGGER.error("Client error in closed loop: {}", exception.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                if (!warmupDuration.isZero() && !warmupDuration.isNegative()) {
                    LOGGER.info("Warmup phase running for {}", warmupDuration);
                    Thread.sleep(warmupDuration.toMillis());
                }

                LOGGER.info("Measurement phase running for {}", measurementDuration);
                recording.set(true);
                Thread.sleep(measurementDuration.toMillis());

                recording.set(false);
                stop.set(true);
                latch.await();
            }
        }
        LOGGER.info("Closed loop load generator completed successfully for {}", configName != null ? configName : "default");
    }

    private void executeRequest(
            HttpClient client,
            String query,
            BufferedWriter writer,
            AtomicBoolean recording,
            Object writeLock) throws IOException, InterruptedException {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String relativePath = "/search?q=" + encoded;
        URI uri = URI.create(baseUrl + relativePath);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();

        Instant instant = Instant.now();
        long startNanos = System.nanoTime();
        int status;
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            status = response.statusCode();
        } catch (Exception exception) {
            status = 500;
        }
        long latencyUs = (System.nanoTime() - startNanos) / 1000;

        if (recording.get() && writer != null) {
            String line = instant + "," + relativePath + "," + status + "," + latencyUs + "\n";
            synchronized (writeLock) {
                writer.write(line);
            }
        }
    }

    public static void main(String[] args) {
        OptionalInt clients = args.length == ARGUMENTS ? positive(args[2]) : OptionalInt.empty();
        if (clients.isEmpty()) {
            LOGGER.error(USAGE);
            System.exit(WRONG_USAGE);
        }
        try {
            SearchLoadGenerator generator = new SearchLoadGenerator(args[0], loadQueries(findQueriesFile()), RunContext.fromPath(Paths.get(args[1])));
            generator.run(clients.getAsInt(), seconds(WARMUP_VARIABLE, DEFAULT_WARMUP), seconds(MEASUREMENT_VARIABLE, DEFAULT_MEASUREMENT), args[3]);
        } catch (Exception exception) {
            LOGGER.error("Load generator failed: {}", exception.getMessage(), exception);
            System.exit(FAILED);
        }
    }

    private static OptionalInt positive(String number) {
        try {
            int value = Integer.parseInt(number);
            return value > 0 ? OptionalInt.of(value) : OptionalInt.empty();
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private static Duration seconds(String variable, Duration otherwise) {
        String value = System.getenv(variable);
        return value == null ? otherwise : Duration.ofSeconds(Long.parseLong(value));
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
