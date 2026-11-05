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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.zip.GZIPOutputStream;

/** {@code bench load}: the closed-loop load generator of {@code search-latency}, one row per measured request in {@code load/<trial>.csv.gz} (SPEC §9). */
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
    private static final String HEADER = "instant,path,status,latency_us\n";
    private static final String TRIAL_SUFFIX = ".csv.gz";
    private static final String UNSAFE_FILE_CHARACTERS = "[^a-zA-Z0-9_=-]";
    private static final int UNANSWERED_STATUS = 500;
    private static final long NANOS_PER_MICRO = 1_000;

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

    /** Runs {@code concurrency} closed-loop clients and writes the requests of the measurement to {@code load/<trial>.csv.gz}. */
    public void run(int concurrency, Duration warmup, Duration measurement, String trial) throws IOException, InterruptedException {
        Path trialFile = runContext.loadDirectory().resolve(trial.replaceAll(UNSAFE_FILE_CHARACTERS, "_") + TRIAL_SUFFIX);
        LOGGER.info("Starting closed loop load generator: {} clients, warmup={}, measurement={}", concurrency, warmup, measurement);
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(
                Files.newOutputStream(trialFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)), StandardCharsets.UTF_8))) {
            writer.write(HEADER);
            new ClosedLoop(writer).run(concurrency, warmup, measurement);
        }
        LOGGER.info("Closed loop load generator completed successfully for {}", trial);
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

    /** The clients of one trial, which only record their requests while the measurement runs. */
    private final class ClosedLoop {

        private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        private final AtomicInteger queryIndex = new AtomicInteger();
        private final AtomicBoolean recording = new AtomicBoolean();
        private final AtomicBoolean stop = new AtomicBoolean();
        private final BufferedWriter writer;

        ClosedLoop(BufferedWriter writer) {
            this.writer = writer;
        }

        void run(int concurrency, Duration warmup, Duration measurement) throws InterruptedException {
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                IntStream.range(0, concurrency).forEach(clientIndex -> executor.submit(this::sendUntilStopped));
                try {
                    measure(warmup, measurement);
                } finally {
                    stop.set(true);
                }
            }
        }

        private void measure(Duration warmup, Duration measurement) throws InterruptedException {
            LOGGER.info("Warmup phase running for {}", warmup);
            Thread.sleep(warmup);
            LOGGER.info("Measurement phase running for {}", measurement);
            recording.set(true);
            Thread.sleep(measurement);
            recording.set(false);
        }

        private void sendUntilStopped() {
            try {
                while (!stop.get() && !Thread.currentThread().isInterrupted()) {
                    send(queries.get(Math.floorMod(queryIndex.getAndIncrement(), queries.size())));
                }
            } catch (Exception exception) {
                LOGGER.error("Client error in closed loop: {}", exception.getMessage());
            }
        }

        private void send(String query) throws IOException {
            String relativePath = "/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + relativePath)).timeout(TIMEOUT).GET().build();
            Instant instant = Instant.now();
            long startNanos = System.nanoTime();
            int status = status(request);
            long latencyUs = (System.nanoTime() - startNanos) / NANOS_PER_MICRO;
            if (recording.get()) {
                String line = instant + "," + relativePath + "," + status + "," + latencyUs + "\n";
                synchronized (writer) {
                    writer.write(line);
                }
            }
        }

        private int status(HttpRequest request) {
            try {
                return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            } catch (IOException e) {
                return UNANSWERED_STATUS;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return UNANSWERED_STATUS;
            }
        }
    }
}
