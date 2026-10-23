package org.ulpgc.tarantino.crawler.benchmarking.support.results;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.util.ListStatistics;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

public final class JmhResults {

    private static final List<String> STRUCTURE_PARAMS = List.of("layout", "index", "metadata");

    private JmhResults() {
    }

    /**
     * Runs the passes one after the other and gives each benchmark configuration the processes of every pass,
     * as a single run forking that many processes would (SPEC §11).
     */
    public static Collection<RunResult> run(List<Options> passes) throws RunnerException {
        Map<String, RunResult> results = new LinkedHashMap<>();
        for (Options pass : passes) {
            if (pass.getResult().hasValue()) {
                createParentDirectories(pass.getResult().get());
            }
            new Runner(pass).run().forEach(result -> results.merge(result.getParams().id(), result, JmhResults::combined));
        }
        return results.values();
    }

    public static List<ResultRow> rows(Collection<RunResult> results, Map<String, Metric> metrics) {
        return results.stream().map(result -> mappedRow(result, metrics)).flatMap(Optional::stream).toList();
    }

    /**
     * A row whose samples are one value per measured iteration of every process (SPEC §11), such as the mean
     * or a percentile of the times sampled during each second, instead of JMH's score over every single time.
     */
    public static ResultRow perIteration(RunResult result, String metric, String unit,
                                         ToDoubleFunction<Result<?>> value) {
        ListStatistics samples = new ListStatistics();
        result.getBenchmarkResults().forEach(process -> process.getIterationResults()
                .forEach(iteration -> samples.addValue(value.applyAsDouble(iteration.getPrimaryResult()))));
        BenchmarkParams params = result.getParams();
        return new ResultRow(structure(params), metric, books(params), samples.getMean(), samples.getMeanErrorAt(ResultRow.CONFIDENCE), unit);
    }

    public static String method(RunResult result) {
        return method(result.getParams());
    }

    private static void createParentDirectories(String file) {
        try {
            Files.createDirectories(Path.of(file).toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static RunResult combined(RunResult first, RunResult second) {
        List<BenchmarkResult> processes = new ArrayList<>(first.getBenchmarkResults());
        processes.addAll(second.getBenchmarkResults());
        return new RunResult(first.getParams(), processes);
    }

    private static Optional<ResultRow> mappedRow(RunResult result, Map<String, Metric> metrics) {
        BenchmarkParams params = result.getParams();
        return Optional.ofNullable(metrics.get(method(params))).map(metric -> row(params, metric, result.getPrimaryResult()));
    }

    private static ResultRow row(BenchmarkParams params, Metric metric, Result<?> result) {
        // Not result.getScoreError(): JMH computes it at a fixed 99.9%
        double error = result.getStatistics().getMeanErrorAt(ResultRow.CONFIDENCE);
        return metric.row(structure(params), books(params), result.getScore(), error);
    }

    private static int books(BenchmarkParams params) {
        return Integer.parseInt(params.getParam("books"));
    }

    private static String structure(BenchmarkParams params) {
        return STRUCTURE_PARAMS.stream().map(params::getParam).filter(Objects::nonNull).findFirst().orElseThrow();
    }

    private static String method(BenchmarkParams params) {
        return params.getBenchmark().substring(params.getBenchmark().lastIndexOf('.') + 1);
    }
}
