package org.ulpgc.tarantino.query.benchmarking;

import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.RunnerException;
import org.ulpgc.tarantino.crawler.benchmarking.support.environment.BenchmarkOptions;
import org.ulpgc.tarantino.crawler.benchmarking.support.files.FootprintLog;
import org.ulpgc.tarantino.crawler.benchmarking.support.files.ResultsFile;
import org.ulpgc.tarantino.crawler.benchmarking.support.results.JmhResults;
import org.ulpgc.tarantino.crawler.benchmarking.support.results.Metric;
import org.ulpgc.tarantino.crawler.benchmarking.support.results.ResultRow;
import org.ulpgc.tarantino.indexer.benchmarking.support.PrebuiltIndexes;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class BenchmarkRunner {

    public static final String SERVICE = "query";
    private static final String QUERY_TIME_UNIT = "µs/query";
    private static final Map<String, Metric> METRICS = Map.of(
            "bookByIdTime", Metric.asMeasured("book_by_id_time", "µs/op"),
            "booksByAuthorTime", Metric.asMeasured("books_by_author_time", "µs/op"),
            "indexOpenTime", Metric.asMeasured("index_open_time", "ms"));

    public static void main(String[] args) throws RunnerException {
        FootprintLog.drain(SERVICE);
        PrebuiltIndexes.deleteAll();
        try {
            Collection<RunResult> results = JmhResults.run(BenchmarkOptions.passes(SERVICE));
            System.out.println("Results written to " + ResultsFile.write(SERVICE, rows(results)));
        } finally {
            PrebuiltIndexes.deleteAll();
        }
    }

    private static List<ResultRow> rows(Collection<RunResult> results) {
        return Stream.of(JmhResults.rows(results, METRICS), queryTimeRows(results), FootprintLog.drain(SERVICE))
                .flatMap(List::stream)
                .toList();
    }

    private static List<ResultRow> queryTimeRows(Collection<RunResult> results) {
        return results.stream()
                .filter(result -> JmhResults.method(result).equals("queryTime"))
                .flatMap(BenchmarkRunner::queryTimeRows)
                .toList();
    }

    // query_time_<category> comes from the benchmark itself through FootprintLog: one sample per measured second
    private static Stream<ResultRow> queryTimeRows(RunResult result) {
        return Stream.of(
                JmhResults.perIteration(result, "query_time", QUERY_TIME_UNIT, Result::getScore),
                JmhResults.perIteration(result, "query_time_p99", QUERY_TIME_UNIT, sampled -> sampled.getStatistics().getPercentile(99)));
    }
}
