package org.ulpgc.tarantino.bench.throughput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.bench.coordination.TransitionLine;
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
 * The throughput files of a run with trials (SPEC §9): a CSV with one row per trial, with its factors, its repetition and
 * each window, and one row in {@code summary.csv} per metric and configuration over its repetitions.
 *
 * @param <T> what {@code measure} reads from the transition lines of a trial
 */
public final class ThroughputReport<T> {

    public static final int DONE = 0;
    public static final int FAILED = 1;

    private static final Logger LOGGER = LoggerFactory.getLogger(ThroughputReport.class);
    private static final String REPETITION = "repetition";
    private static final List<String> WINDOW_COLUMNS = List.of("books", "seconds", "books_per_second");
    private static final String BOOKS_PER_SECOND = "books/s";
    private static final String SEPARATOR = ",";
    private static final String DECIMAL = "%.3f";

    /** A window of {@code T}, with the metric that names it in {@code summary.csv} and the prefix of its CSV columns. */
    public record Series<T>(String metric, String columnPrefix, Function<T, ThroughputWindow> window) {
    }

    private final String fileName;
    private final Function<List<TransitionLine>, Optional<T>> measure;
    private final List<Series<T>> series;

    public ThroughputReport(String fileName, Function<List<TransitionLine>, Optional<T>> measure, List<Series<T>> series) {
        this.fileName = fileName;
        this.measure = measure;
        this.series = series;
    }

    /** Writes both files; {@link #FAILED} when no trial could be measured or the trials do not share their factors. */
    public int writeFor(Path runDirectory) {
        Map<String, Optional<T>> measured = measure(runDirectory);
        List<String> unmeasured = measured.entrySet().stream().filter(trial -> trial.getValue().isEmpty()).map(Map.Entry::getKey).toList();
        if (!unmeasured.isEmpty()) {
            LOGGER.warn("Trials of {} without books to measure: {}", runDirectory, unmeasured);
        }
        Map<String, T> throughputs = present(measured);
        if (throughputs.isEmpty()) {
            LOGGER.error("{} has no trial with books to measure in {}/<trial>/", runDirectory, TransitionLogs.FOLDER);
            return FAILED;
        }
        List<List<String>> factorNames = throughputs.keySet().stream().map(trial -> List.copyOf(TrialName.factors(trial).keySet())).distinct().toList();
        if (factorNames.size() > 1) {
            LOGGER.error("The trials of {} do not share their factors: {}", runDirectory, factorNames);
            return FAILED;
        }
        LOGGER.info("Wrote the throughput of {} trials to {}", throughputs.size(), write(runDirectory.resolve(fileName), factorNames.getFirst(), throughputs));
        appendSummary(runDirectory.resolve(SummaryStats.FILE_NAME), throughputs);
        return DONE;
    }

    /** The throughput of each trial of a run with trials, in {@link TrialName#ORDER}; empty without {@code logs/}. */
    public Map<String, T> throughputs(Path runDirectory) {
        return present(measure(runDirectory));
    }

    private Map<String, Optional<T>> measure(Path runDirectory) {
        Path logs = runDirectory.resolve(TransitionLogs.FOLDER);
        if (!Files.isDirectory(logs)) {
            return Map.of();
        }
        return TransitionLogs.byTrial(logs).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, trial -> measure.apply(trial.getValue()), (first, second) -> first, LinkedHashMap::new));
    }

    private Map<String, T> present(Map<String, Optional<T>> measured) {
        return measured.entrySet().stream().filter(trial -> trial.getValue().isPresent())
                .collect(Collectors.toMap(Map.Entry::getKey, trial -> trial.getValue().orElseThrow(), (first, second) -> first, LinkedHashMap::new));
    }

    private Path write(Path csv, List<String> factorNames, Map<String, T> throughputs) {
        Stream<String> windowColumns = series.stream().flatMap(one -> WINDOW_COLUMNS.stream().map(column -> one.columnPrefix() + column));
        String header = Stream.of(factorNames.stream(), Stream.of(REPETITION), windowColumns).flatMap(Function.identity()).collect(Collectors.joining(SEPARATOR));
        Stream<String> rows = throughputs.entrySet().stream().map(trial -> row(trial.getKey(), trial.getValue()));
        try {
            return Files.writeString(csv, Stream.concat(Stream.of(header), rows).map(line -> line + "\n").collect(Collectors.joining()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String row(String trial, T throughput) {
        return Stream.of(TrialName.factors(trial).values().stream(), Stream.of(String.valueOf(TrialName.repetition(trial))),
                        series.stream().flatMap(one -> cells(one.window().apply(throughput))))
                .flatMap(Function.identity())
                .collect(Collectors.joining(SEPARATOR));
    }

    private static Stream<String> cells(ThroughputWindow window) {
        return Stream.of(String.valueOf(window.books()), decimal(window.seconds()), decimal(window.booksPerSecond()));
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, DECIMAL, value);
    }

    private void appendSummary(Path summaryCsv, Map<String, T> throughputs) {
        throughputs.entrySet().stream()
                .collect(Collectors.groupingBy(trial -> TrialName.configuration(trial.getKey()), LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())))
                .forEach((configuration, repetitions) -> series.forEach(one -> appendRow(summaryCsv, one, configuration, repetitions)));
    }

    private void appendRow(Path summaryCsv, Series<T> one, String configuration, List<T> repetitions) {
        try {
            SummaryStats.appendToSummary(summaryCsv, one.metric(), configuration,
                    SummaryStats.calculate(repetitions.stream().map(one.window()).map(ThroughputWindow::booksPerSecond).toList()), BOOKS_PER_SECOND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
