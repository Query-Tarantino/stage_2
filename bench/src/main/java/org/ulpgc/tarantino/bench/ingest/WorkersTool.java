package org.ulpgc.tarantino.bench.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.bench.coordination.TransitionLogs;
import org.ulpgc.tarantino.bench.run.TrialName;
import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code bench ingest-workers <run directory>}: writes {@code workers.csv}, one row per trial of an
 * {@code ingest-throughput} run with its factors, its repetition and the mean work and gap of its download workers
 * ({@link WorkerTimes}), and appends both to {@code summary.csv} per configuration over its repetitions (SPEC §9).
 */
public final class WorkersTool {

    public static final String FILE_NAME = "workers.csv";
    static final int DONE = 0;
    static final int FAILED = 1;
    static final int WRONG_USAGE = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkersTool.class);
    private static final String LOG_SUFFIX = ".log";
    private static final String SEPARATOR = ",";
    private static final String DECIMAL = "%.3f";
    private static final String MILLISECONDS = "ms";
    private static final List<String> COLUMNS = List.of("repetition", "books", "work_ms", "gap_ms");
    private static final List<Map.Entry<String, Function<WorkerTimes, List<Double>>>> METRICS = List.of(
            Map.entry("download_work", WorkerTimes::workMillis),
            Map.entry("download_gap", WorkerTimes::gapMillis));

    private WorkersTool() {
    }

    public static int run(List<String> args) {
        if (args.size() != 1) {
            LOGGER.error("Usage: bench ingest-workers <run directory>");
            return WRONG_USAGE;
        }
        Path runDirectory = Path.of(args.getFirst());
        Map<String, WorkerTimes> trials = trials(runDirectory.resolve(TransitionLogs.FOLDER));
        if (trials.isEmpty()) {
            LOGGER.error("{} has no trial with download workers in {}/<trial>/", runDirectory, TransitionLogs.FOLDER);
            return FAILED;
        }
        write(runDirectory.resolve(FILE_NAME), trials);
        appendSummary(runDirectory.resolve(SummaryStats.FILE_NAME), trials);
        LOGGER.info("Wrote the download workers of {} trials to {}", trials.size(), runDirectory.resolve(FILE_NAME));
        return DONE;
    }

    private static Map<String, WorkerTimes> trials(Path logs) {
        if (!Files.isDirectory(logs)) {
            return Map.of();
        }
        try (Stream<Path> entries = Files.list(logs)) {
            return entries.filter(Files::isDirectory)
                    .map(trial -> trial.getFileName().toString())
                    .filter(TrialName::isValid)
                    .sorted(TrialName.ORDER)
                    .flatMap(trial -> times(logs.resolve(trial)).map(times -> Map.entry(trial, times)).stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first, LinkedHashMap::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<WorkerTimes> times(Path trialLogs) {
        try (Stream<Path> files = Files.list(trialLogs)) {
            return WorkerTimes.of(files.filter(file -> file.getFileName().toString().endsWith(LOG_SUFFIX))
                    .sorted()
                    .flatMap(WorkersTool::lines)
                    .flatMap(line -> WorkerLine.parse(line).stream())
                    .toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Stream<String> lines(Path log) {
        try {
            return Files.readAllLines(log).stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path csv, Map<String, WorkerTimes> trials) {
        String header = Stream.concat(TrialName.factors(trials.keySet().iterator().next()).keySet().stream(), COLUMNS.stream())
                .collect(Collectors.joining(SEPARATOR));
        Stream<String> rows = trials.entrySet().stream().map(trial -> row(trial.getKey(), trial.getValue()));
        try {
            Files.writeString(csv, Stream.concat(Stream.of(header), rows).map(line -> line + "\n").collect(Collectors.joining()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String row(String trial, WorkerTimes times) {
        Stream<String> cells = Stream.of(String.valueOf(TrialName.repetition(trial)), String.valueOf(times.workMillis().size()),
                decimal(mean(times.workMillis())), decimal(mean(times.gapMillis())));
        return Stream.concat(TrialName.factors(trial).values().stream(), cells).collect(Collectors.joining(SEPARATOR));
    }

    private static void appendSummary(Path summaryCsv, Map<String, WorkerTimes> trials) {
        Map<String, List<WorkerTimes>> byConfiguration = trials.entrySet().stream()
                .collect(Collectors.groupingBy(trial -> TrialName.configuration(trial.getKey()), LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        byConfiguration.forEach((configuration, repetitions) -> METRICS.forEach(metric -> append(summaryCsv,
                metric.getKey(), configuration, repetitions.stream().map(metric.getValue()).toList())));
    }

    private static void append(Path summaryCsv, String metric, String configuration, List<List<Double>> valuesByRepetition) {
        try {
            SummaryStats.appendToSummary(summaryCsv, metric, configuration, SummaryStats.ofRepetitions(valuesByRepetition), MILLISECONDS);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, DECIMAL, value);
    }
}
