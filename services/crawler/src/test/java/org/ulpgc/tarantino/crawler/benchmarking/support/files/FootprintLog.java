package org.ulpgc.tarantino.crawler.benchmarking.support.files;

import org.openjdk.jmh.util.ListStatistics;
import org.ulpgc.tarantino.crawler.benchmarking.support.environment.BenchmarkPaths;
import org.ulpgc.tarantino.crawler.benchmarking.support.results.ResultRow;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class FootprintLog {

    private FootprintLog() {
    }

    /** Exact measures, equal in every benchmark process; drained once. */
    public static void append(String service, List<ResultRow> rows) {
        append(file(service), rows);
    }

    /** One measure per benchmark process; drained as the mean of every process, with its confidence interval. */
    public static void appendSample(String service, ResultRow sample) {
        append(samplesFile(service), List.of(sample));
    }

    public static List<ResultRow> drain(String service) {
        return drain(file(service), samplesFile(service));
    }

    static List<ResultRow> drain(Path exactFile, Path samplesFile) {
        try {
            List<ResultRow> rows = Stream.concat(exactRows(exactFile), meanRows(samplesFile)).toList();
            Files.deleteIfExists(exactFile);
            Files.deleteIfExists(samplesFile);
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void append(Path file, List<ResultRow> rows) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, rows.stream().map(ResultRow::csvLine).toList(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Stream<ResultRow> exactRows(Path file) throws IOException {
        Map<String, ResultRow> firstPerMeasure = new LinkedHashMap<>();
        rows(file).forEach(row -> firstPerMeasure.putIfAbsent(measure(row), row));
        return firstPerMeasure.values().stream();
    }

    private static Stream<ResultRow> meanRows(Path file) throws IOException {
        Map<String, List<ResultRow>> samplesPerMeasure = new LinkedHashMap<>();
        rows(file).forEach(row -> samplesPerMeasure.computeIfAbsent(measure(row), key -> new ArrayList<>()).add(row));
        return samplesPerMeasure.values().stream().map(FootprintLog::mean);
    }

    private static ResultRow mean(List<ResultRow> samples) {
        ListStatistics statistics = new ListStatistics();
        samples.forEach(sample -> statistics.addValue(sample.value()));
        ResultRow first = samples.getFirst();
        return new ResultRow(first.structure(), first.metric(), first.books(), statistics.getMean(), statistics.getMeanErrorAt(ResultRow.CONFIDENCE), first.unit());
    }

    private static List<ResultRow> rows(Path file) throws IOException {
        return Files.exists(file) ? Files.readAllLines(file).stream().map(ResultRow::parse).toList() : List.of();
    }

    private static String measure(ResultRow row) {
        return String.join(",", row.structure(), row.metric(), String.valueOf(row.books()));
    }

    private static Path file(String service) {
        return BenchmarkPaths.scratch("footprint-" + service + ".csv");
    }

    private static Path samplesFile(String service) {
        return BenchmarkPaths.scratch("footprint-samples-" + service + ".csv");
    }
}
