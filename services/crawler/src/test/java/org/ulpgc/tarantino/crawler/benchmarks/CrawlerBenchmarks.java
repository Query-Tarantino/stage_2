package org.ulpgc.tarantino.crawler.benchmarks;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs the crawler's JMH benchmarks, measured as {@link BenchmarkProtocol} says, into the {@code jmh/} folder of a run
 * (SPEC §9) as JSON, which {@code bench} turns into CSV.
 */
public final class CrawlerBenchmarks {

    private CrawlerBenchmarks() {
    }

    /** {@code args[0]} is the run directory, {@code benchmarks/runs/<run_id>}. */
    public static void main(String[] args) throws IOException, RunnerException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: CrawlerBenchmarks <run directory>");
        }
        Path jmh = Files.createDirectories(Path.of(args[0], "jmh"));
        Options options = new OptionsBuilder()
                .include(CrawlerBenchmarks.class.getPackageName() + "\\.\\w+Benchmark")
                .resultFormat(ResultFormatType.JSON)
                .result(jmh.resolve("crawler.json").toString())
                .build();
        new Runner(options).run();
    }
}
