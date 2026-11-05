package org.ulpgc.tarantino.bench.load;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.bench.run.TrialName;
import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * {@code bench search-latency <run directory>}: appends to {@code summary.csv}, for each configuration of a
 * {@code search-latency} run, the mean and the p50, p95 and p99 latency of its trials in µs and their throughput in
 * requests/s, over its repetitions (SPEC §9), from the {@code 200} answers in {@code load/<trial>.csv.gz}.
 */
public final class SearchLatencyTool {

    static final int DONE = 0;
    static final int FAILED = 1;
    static final int WRONG_USAGE = 2;

    static final String LATENCY_MEAN = "search_latency_mean";
    static final String LATENCY_P50 = "search_latency_p50";
    static final String LATENCY_P95 = "search_latency_p95";
    static final String LATENCY_P99 = "search_latency_p99";
    static final String THROUGHPUT = "search_throughput";

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchLatencyTool.class);
    private static final String LOAD_FOLDER = "load";
    private static final String TRIAL_SUFFIX = ".csv.gz";
    private static final String OK = "200";
    private static final String MICROSECONDS = "us";
    private static final String REQUESTS_PER_SECOND = "requests/s";
    private static final double NANOS_PER_SECOND = 1e9;
    private static final int INSTANT = 0;
    private static final int STATUS = 2;
    private static final int LATENCY = 3;

    /** The answered requests of one trial: their latencies in µs, and how many came per second from the first to the last. */
    record TrialLoad(List<Long> latencies, double throughput) {
    }

    private SearchLatencyTool() {
    }

    public static int run(List<String> args) {
        if (args.size() != 1) {
            LOGGER.error("Usage: bench search-latency <run directory>");
            return WRONG_USAGE;
        }
        Path runDirectory = Path.of(args.getFirst());
        Map<String, List<TrialLoad>> repetitions = repetitionsByConfiguration(runDirectory.resolve(LOAD_FOLDER));
        if (repetitions.isEmpty()) {
            LOGGER.error("{} has no trial files in {}", runDirectory, LOAD_FOLDER);
            return FAILED;
        }
        Path summaryCsv = runDirectory.resolve(SummaryStats.FILE_NAME);
        repetitions.forEach((configuration, trials) -> appendRows(summaryCsv, configuration, trials));
        LOGGER.info("Summarised the search latency of {} configurations in {}", repetitions.size(), summaryCsv);
        return DONE;
    }

    private static Map<String, List<TrialLoad>> repetitionsByConfiguration(Path load) {
        if (!Files.isDirectory(load)) {
            return Map.of();
        }
        try (Stream<Path> files = Files.list(load)) {
            return files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(TRIAL_SUFFIX))
                    .map(name -> name.substring(0, name.length() - TRIAL_SUFFIX.length())).filter(TrialName::isValid)
                    .sorted(TrialName.ORDER).collect(Collectors.groupingBy(TrialName::configuration, LinkedHashMap::new,
                            Collectors.mapping(trial -> read(load.resolve(trial + TRIAL_SUFFIX)), Collectors.toList())));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static TrialLoad read(Path trialCsv) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(trialCsv)), StandardCharsets.UTF_8))) {
            List<String[]> answered = reader.lines().skip(1).map(line -> line.split(",")).filter(row -> OK.equals(row[STATUS])).toList();
            return new TrialLoad(answered.stream().map(row -> Long.parseLong(row[LATENCY])).toList(), throughput(answered));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static double throughput(List<String[]> answered) {
        List<Instant> instants = answered.stream().map(row -> Instant.parse(row[INSTANT])).sorted().toList();
        if (instants.size() < 2) {
            return 0.0;
        }
        return instants.size() / (Duration.between(instants.getFirst(), instants.getLast()).toNanos() / NANOS_PER_SECOND);
    }

    private static void appendRows(Path summaryCsv, String configuration, List<TrialLoad> trials) {
        List<Long> pooled = trials.stream().flatMap(trial -> trial.latencies().stream()).toList();
        appendRow(summaryCsv, LATENCY_MEAN, configuration, trials, SummaryStats.Metrics::mean, pooled);
        appendRow(summaryCsv, LATENCY_P50, configuration, trials, SummaryStats.Metrics::p50, pooled);
        appendRow(summaryCsv, LATENCY_P95, configuration, trials, SummaryStats.Metrics::p95, pooled);
        appendRow(summaryCsv, LATENCY_P99, configuration, trials, SummaryStats.Metrics::p99, pooled);
        List<Double> throughputs = trials.stream().map(TrialLoad::throughput).toList();
        append(summaryCsv, THROUGHPUT, configuration, SummaryStats.overRepetitions(throughputs, throughputs), REQUESTS_PER_SECOND);
    }

    private static void appendRow(Path summaryCsv, String metric, String configuration, List<TrialLoad> trials,
                                  Function<SummaryStats.Metrics, Double> figure, List<Long> pooled) {
        List<Double> perTrial = trials.stream().map(trial -> figure.apply(SummaryStats.calculate(trial.latencies()))).toList();
        append(summaryCsv, metric, configuration, SummaryStats.overRepetitions(perTrial, pooled), MICROSECONDS);
    }

    private static void append(Path summaryCsv, String metric, String configuration, SummaryStats.Metrics metrics, String unit) {
        try {
            SummaryStats.appendToSummary(summaryCsv, metric, configuration, metrics, unit);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
