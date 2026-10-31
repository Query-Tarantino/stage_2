package org.ulpgc.tarantino.bench.coordination;

import org.ulpgc.tarantino.bench.run.TrialName;
import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The {@code summary.csv} rows of a run with trials (SPEC §9): for each configuration, the metrics of every column of
 * {@code coordination.csv} over the published books of all its repetitions, in milliseconds.
 */
final class CoordinationSummary {

    static final String FILE_NAME = "summary.csv";
    static final String UNIT = "ms";

    private static final String UNIT_SUFFIX = "_ms";

    private CoordinationSummary() {
    }

    static void append(Path runDirectory, Map<String, List<BookCoordination>> booksByTrial) {
        booksByConfiguration(booksByTrial).forEach((configuration, books) ->
                IntStream.range(0, CoordinationTool.TIME_COLUMNS.size()).forEach(column ->
                        appendRow(runDirectory.resolve(FILE_NAME), metric(column), configuration, millis(books, column))));
    }

    private static Map<String, List<BookCoordination>> booksByConfiguration(Map<String, List<BookCoordination>> booksByTrial) {
        return booksByTrial.entrySet().stream().collect(Collectors.groupingBy(trial -> TrialName.configuration(trial.getKey()),
                LinkedHashMap::new, Collectors.flatMapping(trial -> trial.getValue().stream(), Collectors.toList())));
    }

    private static String metric(int column) {
        return CoordinationTool.TIME_COLUMNS.get(column).replace(UNIT_SUFFIX, "");
    }

    private static List<Long> millis(List<BookCoordination> books, int column) {
        return books.stream().map(book -> book.times().get(column)).map(Duration::toMillis).toList();
    }

    private static void appendRow(Path summaryCsv, String metric, String configuration, List<Long> values) {
        try {
            SummaryStats.appendToSummary(summaryCsv, metric, configuration, SummaryStats.calculate(values), UNIT);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
