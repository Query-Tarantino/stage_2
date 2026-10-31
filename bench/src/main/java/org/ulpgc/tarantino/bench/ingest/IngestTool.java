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
 * {@code bench ingest-throughput <run directory>}: writes {@code ingest.csv}, one row per trial of an
 * {@code ingest-throughput} run with its factors, repetition and both windows of {@link IngestThroughput}, and appends
 * to {@code summary.csv} the throughput of each configuration over its repetitions (SPEC §9).
 */
public final class IngestTool {

    static final int DONE = 0;
    static final int FAILED = 1;
    static final int WRONG_USAGE = 2;
    public static final String FILE_NAME = "ingest.csv";
    static final String THROUGHPUT = "ingest_throughput";
    static final String STEADY_THROUGHPUT = "ingest_throughput_steady";
    static final List<String> MEASURE_COLUMNS = List.of("repetition", "books", "seconds", "books_per_second",
            "steady_books", "steady_seconds", "steady_books_per_second");

    private static final Logger LOGGER = LoggerFactory.getLogger(IngestTool.class);
    private static final String BOOKS_PER_SECOND = "books/s";
    private static final String SEPARATOR = ",";
    private static final String DECIMAL = "%.3f";

    private IngestTool() {
    }

    public static int run(List<String> args) {
        if (args.size() != 1) {
            LOGGER.error("Usage: bench ingest-throughput <run directory>");
            return WRONG_USAGE;
        }
        Path runDirectory = Path.of(args.getFirst());
        Map<String, Optional<IngestThroughput>> measured = measure(runDirectory);
        List<String> unmeasured = measured.entrySet().stream().filter(trial -> trial.getValue().isEmpty()).map(Map.Entry::getKey).toList();
        if (!unmeasured.isEmpty()) {
            LOGGER.warn("Trials of {} without downloads to measure: {}", runDirectory, unmeasured);
        }
        Map<String, IngestThroughput> throughputs = present(measured);
        if (throughputs.isEmpty()) {
            LOGGER.error("{} has no trial with downloads in {}/<trial>/", runDirectory, TransitionLogs.FOLDER);
            return FAILED;
        }
        List<List<String>> factorNames = throughputs.keySet().stream().map(trial -> List.copyOf(TrialName.factors(trial).keySet())).distinct().toList();
        if (factorNames.size() > 1) {
            LOGGER.error("The trials of {} do not share their factors: {}", runDirectory, factorNames);
            return FAILED;
        }
        LOGGER.info("Wrote the throughput of {} trials to {}", throughputs.size(), write(runDirectory.resolve(FILE_NAME), factorNames.getFirst(), throughputs));
        appendSummary(runDirectory.resolve(SummaryStats.FILE_NAME), throughputs);
        return DONE;
    }

    /** The throughput of each trial of a run with trials, in {@link TrialName#ORDER}; empty without {@code logs/}. */
    public static Map<String, IngestThroughput> throughputs(Path runDirectory) {
        return present(measure(runDirectory));
    }

    private static Map<String, Optional<IngestThroughput>> measure(Path runDirectory) {
        Path logs = runDirectory.resolve(TransitionLogs.FOLDER);
        if (!Files.isDirectory(logs)) {
            return Map.of();
        }
        return TransitionLogs.byTrial(logs).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, trial -> IngestThroughput.of(trial.getValue()), (first, second) -> first, LinkedHashMap::new));
    }

    private static Map<String, IngestThroughput> present(Map<String, Optional<IngestThroughput>> measured) {
        return measured.entrySet().stream().filter(trial -> trial.getValue().isPresent())
                .collect(Collectors.toMap(Map.Entry::getKey, trial -> trial.getValue().orElseThrow(), (first, second) -> first, LinkedHashMap::new));
    }

    private static Path write(Path csv, List<String> factorNames, Map<String, IngestThroughput> throughputs) {
        String header = Stream.concat(factorNames.stream(), MEASURE_COLUMNS.stream()).collect(Collectors.joining(SEPARATOR));
        Stream<String> rows = throughputs.entrySet().stream().map(trial -> row(trial.getKey(), trial.getValue()));
        try {
            return Files.writeString(csv, Stream.concat(Stream.of(header), rows).map(line -> line + "\n").collect(Collectors.joining()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String row(String trial, IngestThroughput throughput) {
        return Stream.of(TrialName.factors(trial).values().stream(), Stream.of(String.valueOf(TrialName.repetition(trial))),
                        cells(throughput.whole()), cells(throughput.steady()))
                .flatMap(Function.identity())
                .collect(Collectors.joining(SEPARATOR));
    }

    private static Stream<String> cells(IngestThroughput.Window window) {
        return Stream.of(String.valueOf(window.books()), decimal(window.seconds()), decimal(window.booksPerSecond()));
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, DECIMAL, value);
    }

    private static void appendSummary(Path summaryCsv, Map<String, IngestThroughput> throughputs) {
        throughputs.entrySet().stream()
                .collect(Collectors.groupingBy(trial -> TrialName.configuration(trial.getKey()), LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())))
                .forEach((configuration, repetitions) -> {
                    appendRow(summaryCsv, THROUGHPUT, configuration, repetitions.stream().map(IngestThroughput::whole).toList());
                    appendRow(summaryCsv, STEADY_THROUGHPUT, configuration, repetitions.stream().map(IngestThroughput::steady).toList());
                });
    }

    private static void appendRow(Path summaryCsv, String metric, String configuration, List<IngestThroughput.Window> windows) {
        try {
            SummaryStats.appendToSummary(summaryCsv, metric, configuration,
                    SummaryStats.calculate(windows.stream().map(window -> Math.round(window.booksPerSecond())).toList()), BOOKS_PER_SECOND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
