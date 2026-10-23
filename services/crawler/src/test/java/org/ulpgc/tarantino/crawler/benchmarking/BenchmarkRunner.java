package org.ulpgc.tarantino.crawler.benchmarking;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.RunnerException;
import org.ulpgc.tarantino.crawler.benchmarking.support.datalake.DatalakeFixture;
import org.ulpgc.tarantino.crawler.benchmarking.support.datalake.RecoveryOutcome;
import org.ulpgc.tarantino.crawler.benchmarking.support.datalake.RecoveryScenario;
import org.ulpgc.tarantino.crawler.benchmarking.support.dataset.BenchmarkDataset;
import org.ulpgc.tarantino.crawler.benchmarking.support.environment.BenchmarkOptions;
import org.ulpgc.tarantino.crawler.benchmarking.support.environment.BenchmarkPaths;
import org.ulpgc.tarantino.crawler.benchmarking.support.files.Directories;
import org.ulpgc.tarantino.crawler.benchmarking.support.files.FootprintLog;
import org.ulpgc.tarantino.crawler.benchmarking.support.files.ResultsFile;
import org.ulpgc.tarantino.crawler.benchmarking.support.results.JmhResults;
import org.ulpgc.tarantino.crawler.benchmarking.support.results.Metric;
import org.ulpgc.tarantino.crawler.benchmarking.support.results.ResultRow;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class BenchmarkRunner {

    public static final String SERVICE = "crawler";

    private static final int RECOVERY_BOOKS = 100;
    private static final Map<String, Metric> METRICS = Map.of(
            "writeThroughput", Metric.booksPerSecond("write_throughput"),
            "lookupTime", Metric.asMeasured("lookup_time", "µs/op"),
            "newBooksDetectionTime", Metric.asMeasured("new_books_detection_time", "ms"));

    public static void main(String[] args) throws RunnerException {
        FootprintLog.drain(SERVICE);
        Collection<RunResult> results = JmhResults.run(BenchmarkOptions.passes(SERVICE));
        System.out.println("Results written to " + ResultsFile.write(SERVICE, rows(results)));
    }

    private static List<ResultRow> rows(Collection<RunResult> results) {
        return Stream.of(JmhResults.rows(results, METRICS), FootprintLog.drain(SERVICE), recoveryRows())
                .flatMap(List::stream)
                .toList();
    }

    private static List<ResultRow> recoveryRows() {
        BenchmarkDataset dataset = BenchmarkDataset.fromEnvironment();
        return DatalakeFixture.LAYOUTS.stream().flatMap(layout -> recoveryRows(layout, dataset).stream()).toList();
    }

    private static List<ResultRow> recoveryRows(String layout, BenchmarkDataset dataset) {
        Path root = BenchmarkPaths.scratch("datalake-recovery-" + layout);
        Directories.delete(root);
        RecoveryOutcome outcome = new RecoveryScenario(layout, root, dataset::rawText).run(dataset.ids(RECOVERY_BOOKS));
        Directories.delete(root);
        return List.of(
                ResultRow.exact(layout, "recovery_ok", RECOVERY_BOOKS, outcome.recovered() ? 1 : 0, "0 or 1"),
                ResultRow.exact(layout, "recovery_leftover_files", RECOVERY_BOOKS, outcome.leftoverFiles(), "files"));
    }
}
