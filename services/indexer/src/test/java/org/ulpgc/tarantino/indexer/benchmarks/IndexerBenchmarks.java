package org.ulpgc.tarantino.indexer.benchmarks;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs the indexer's JMH benchmarks into the {@code jmh/} folder of a run (SPEC §9): at least 5 warmup and 10
 * measurement iterations, a fixed heap and JSON output, which {@code bench} turns into CSV.
 */
public final class IndexerBenchmarks {

    private static final int WARMUP_ITERATIONS = 5;
    private static final int MEASUREMENT_ITERATIONS = 10;
    private static final int FORKS = 1;
    private static final String FIXED_HEAP = "-Xmx1g";

    private IndexerBenchmarks() {
    }

    /** {@code args[0]} is the run directory, {@code benchmarks/runs/<run_id>}. */
    public static void main(String[] args) throws IOException, RunnerException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: IndexerBenchmarks <run directory>");
        }
        Path jmh = Files.createDirectories(Path.of(args[0], "jmh"));
        Options options = new OptionsBuilder()
                .include(IndexerBenchmarks.class.getPackageName() + "\\.\\w+Benchmark")
                .warmupIterations(WARMUP_ITERATIONS)
                .measurementIterations(MEASUREMENT_ITERATIONS)
                .forks(FORKS)
                .jvmArgs(FIXED_HEAP)
                .resultFormat(ResultFormatType.JSON)
                .result(jmh.resolve("indexer.json").toString())
                .build();
        new Runner(options).run();
    }
}
