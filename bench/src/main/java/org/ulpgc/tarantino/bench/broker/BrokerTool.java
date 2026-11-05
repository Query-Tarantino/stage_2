package org.ulpgc.tarantino.bench.broker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.bench.run.TrialName;
import org.ulpgc.tarantino.bench.stats.SummaryStats;
import org.ulpgc.tarantino.common.activemq.ActiveMqBroker;
import org.ulpgc.tarantino.common.hazelcast.HazelcastBroker;
import org.ulpgc.tarantino.common.hazelcast.HazelcastClients;
import org.ulpgc.tarantino.common.hazelcast.HazelcastContracts;
import org.ulpgc.tarantino.common.messaging.Broker;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * {@code bench broker <run dir> <trial> <broker> <consumers>}: the latency and throughput of one broker in isolation
 * (SPEC §9), with {@code <broker>} {@code hazelcast}, {@code activemq-nonpersistent} or {@code activemq-persistent}. A
 * latency phase sends {@value #LATENCY_RATE} messages/s for {@value #LATENCY_SECONDS} s and drops the first
 * {@value #WARMUP_SECONDS} s; a throughput phase sends {@value #THROUGHPUT_MESSAGES} messages as fast as possible. The
 * raw latencies go to {@code load/<trial>.csv.gz} and one row per trial to {@code broker.csv}. {@code bench broker <run
 * dir>} then appends {@code broker_latency} and {@code broker_throughput} to {@code summary.csv}, one value per trial.
 */
public final class BrokerTool {

    public static final String FILE_NAME = "broker.csv";

    static final String HEADER = "broker,consumers,repetition,messages,p50_us,p95_us,p99_us,throughput_per_s";

    private static final Logger LOGGER = LoggerFactory.getLogger(BrokerTool.class);
    private static final int P50_COLUMN = 4;
    private static final int THROUGHPUT_COLUMN = 7;
    private static final int DONE = 0;
    private static final int WRONG_USAGE = 2;
    private static final int LATENCY_RATE = 1_000;
    private static final int LATENCY_SECONDS = 35;
    private static final int WARMUP_SECONDS = 5;
    private static final int THROUGHPUT_MESSAGES = 50_000;
    private static final int THROUGHPUT_FIRST_ID = 1_000_000;
    private static final int ACTIVEMQ_PREFETCH = 1;
    private static final Duration CONNECT_DEADLINE = Duration.ofSeconds(60);
    private static final String DEFAULT_HAZELCAST = "hazelcast:5701";
    private static final String DEFAULT_ACTIVEMQ = "failover:(tcp://activemq:61616)?timeout=1000";
    private static final String LOAD_FOLDER = "load";
    private static final String LATENCY_ROUND = "-latency";
    private static final String THROUGHPUT_ROUND = "-throughput";
    private static final String RAW_HEADER = "sent_ns,received_ns,latency_us,consumer\n";
    private static final double NANOS_PER_SECOND = 1e9;

    private BrokerTool() {
    }

    public static int run(List<String> args) {
        if (args.size() == 1) {
            summarise(Path.of(args.getFirst()));
            return DONE;
        }
        if (args.size() != 4) {
            LOGGER.error("Usage: bench broker <run dir> <trial> <hazelcast|activemq-nonpersistent|activemq-persistent> <consumers>, or bench broker <run dir>");
            return WRONG_USAGE;
        }
        try (Broker broker = open(args.get(2))) {
            measure(Path.of(args.getFirst()), args.get(1), args.get(2), Integer.parseInt(args.get(3)), broker);
            return DONE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return WRONG_USAGE;
        }
    }

    private static void measure(Path runDirectory, String trial, String brokerName, int consumers, Broker broker)
            throws InterruptedException {
        awaitConnected(broker);
        BrokerRound latency = new BrokerRound(broker, BrokerRound.QUEUE + "-" + trial + LATENCY_ROUND, consumers);
        Map<Integer, BrokerSample> latencyRound = latency.run(0, LATENCY_RATE * LATENCY_SECONDS,
                Duration.ofSeconds(1).dividedBy(LATENCY_RATE));
        long firstKept = latency.startNanos() + Duration.ofSeconds(WARMUP_SECONDS).toNanos();
        List<BrokerSample> kept = BrokerRound.sentAfter(latencyRound, firstKept);
        Map<Integer, BrokerSample> throughputRound = new BrokerRound(broker, BrokerRound.QUEUE + "-" + trial + THROUGHPUT_ROUND, consumers)
                .run(THROUGHPUT_FIRST_ID, THROUGHPUT_MESSAGES, Duration.ZERO);
        writeRaw(runDirectory.resolve(LOAD_FOLDER).resolve(trial + ".csv.gz"), kept);
        SummaryStats.Metrics latencies = SummaryStats.calculate(kept.stream().map(BrokerSample::latencyMicros).toList());
        appendRow(runDirectory.resolve(FILE_NAME), String.format(Locale.ROOT, "%s,%d,%d,%d,%.0f,%.0f,%.0f,%.1f", brokerName,
                consumers, TrialName.repetition(trial), kept.size(), latencies.p50(), latencies.p95(), latencies.p99(),
                throughput(throughputRound)));
        LOGGER.info("Trial {}: {} messages kept, p50 {} µs, {} of {} throughput messages received", trial, kept.size(),
                latencies.p50(), throughputRound.size(), THROUGHPUT_MESSAGES);
    }

    private static double throughput(Map<Integer, BrokerSample> round) {
        long first = round.values().stream().mapToLong(BrokerSample::sentNanos).min().orElse(0);
        long last = round.values().stream().mapToLong(BrokerSample::receivedNanos).max().orElse(first);
        return last == first ? 0 : round.size() * NANOS_PER_SECOND / (last - first);
    }

    private static Broker open(String brokerName) {
        String hazelcast = Optional.ofNullable(System.getenv("TARANTINO_HAZELCAST")).orElse(DEFAULT_HAZELCAST);
        String activeMq = Optional.ofNullable(System.getenv("TARANTINO_ACTIVEMQ")).orElse(DEFAULT_ACTIVEMQ);
        return switch (brokerName) {
            case "hazelcast" -> HazelcastBroker.of(HazelcastClients.connect(List.of(hazelcast), HazelcastContracts.DEFAULT_CLUSTER));
            case "activemq-nonpersistent" -> ActiveMqBroker.connect(activeMq, false, ACTIVEMQ_PREFETCH);
            case "activemq-persistent" -> ActiveMqBroker.connect(activeMq, true, ACTIVEMQ_PREFETCH);
            default -> throw new IllegalArgumentException("Unknown broker " + brokerName);
        };
    }

    private static void awaitConnected(Broker broker) throws InterruptedException {
        Instant deadline = Instant.now().plus(CONNECT_DEADLINE);
        while (!broker.connected()) {
            if (Instant.now().isAfter(deadline)) {
                throw new IllegalStateException(broker.name() + " did not connect within " + CONNECT_DEADLINE);
            }
            Thread.sleep(Duration.ofMillis(200));
        }
    }

    static void summarise(Path runDirectory) {
        List<String[]> rows = readRows(runDirectory.resolve(FILE_NAME));
        Map<String, List<String[]>> byConfiguration = rows.stream().collect(Collectors.groupingBy(
                row -> "broker=" + row[0] + ",consumers=" + row[1], LinkedHashMap::new, Collectors.toList()));
        byConfiguration.forEach((configuration, trials) -> {
            append(runDirectory, "broker_latency", configuration, trials, P50_COLUMN, "us");
            append(runDirectory, "broker_throughput", configuration, trials, THROUGHPUT_COLUMN, "messages/s");
        });
    }

    private static void append(Path runDirectory, String metric, String configuration, List<String[]> trials, int column, String unit) {
        List<Double> values = trials.stream().map(row -> Double.parseDouble(row[column])).toList();
        try {
            SummaryStats.appendToSummary(runDirectory.resolve(SummaryStats.FILE_NAME), metric, configuration,
                    SummaryStats.calculate(values), unit);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String[]> readRows(Path csv) {
        try {
            return Files.readAllLines(csv).stream().skip(1).filter(line -> !line.isBlank()).map(line -> line.split(","))
                    .sorted(Comparator.comparing(row -> row[0])).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void appendRow(Path csv, String row) {
        try {
            if (!Files.exists(csv)) {
                Files.writeString(csv, HEADER + "\n");
            }
            Files.writeString(csv, row + "\n", StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeRaw(Path gzip, List<BrokerSample> samples) {
        try {
            Files.createDirectories(gzip.getParent());
            try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gzip))) {
                out.write(RAW_HEADER.getBytes(StandardCharsets.UTF_8));
                for (BrokerSample sample : samples.stream().sorted(Comparator.comparingLong(BrokerSample::sentNanos)).toList()) {
                    out.write((sample.row() + "\n").getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
