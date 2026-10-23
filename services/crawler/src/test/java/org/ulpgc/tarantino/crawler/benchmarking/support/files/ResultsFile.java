package org.ulpgc.tarantino.crawler.benchmarking.support.files;

import org.ulpgc.tarantino.crawler.benchmarking.support.environment.BenchmarkPaths;
import org.ulpgc.tarantino.crawler.benchmarking.support.results.ResultRow;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ResultsFile {

    private ResultsFile() {
    }

    public static Path write(String service, List<ResultRow> rows) {
        Path file = BenchmarkPaths.benchmarks().resolve("results").resolve(ResultRow.LANGUAGE + "-" + service + ".csv");
        try {
            Files.createDirectories(file.getParent());
            return Files.writeString(file, content(rows));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String content(List<ResultRow> rows) {
        return Stream.concat(Stream.of(ResultRow.CSV_HEADER), rows.stream().map(ResultRow::csvLine))
                .collect(Collectors.joining("\n", "", "\n"));
    }
}
