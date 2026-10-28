package org.ulpgc.tarantino.crawler.benchmarks;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.crawler.adapters.gutenberg.LocalMirrorDownloader;
import org.ulpgc.tarantino.crawler.model.book.BookText;
import org.ulpgc.tarantino.crawler.model.book.GutenbergText;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * The split of a downloaded text into header and body (SPEC §4.6), on three books of the local mirror
 * ({@code TARANTINO_MIRROR}, or {@code benchmarks/mirror}) of very different sizes.
 */
@State(Scope.Benchmark)
@Warmup(iterations = BenchmarkProtocol.WARMUP_ITERATIONS, time = GutenbergTextBenchmark.ITERATION_SECONDS)
@Measurement(iterations = BenchmarkProtocol.MEASUREMENT_ITERATIONS, time = GutenbergTextBenchmark.ITERATION_SECONDS)
@Fork(value = BenchmarkProtocol.FORKS, jvmArgs = BenchmarkProtocol.FIXED_HEAP)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class GutenbergTextBenchmark {

    static final int ITERATION_SECONDS = 2;

    private static final Path DEFAULT_MIRROR = Path.of("benchmarks", "mirror");

    @Param({"1080", "84", "2600"})
    public int bookId;

    private String rawText;

    @Setup
    public void readTheBook() {
        Path mirror = TarantinoConfig.fromEnvironment(TarantinoService.CRAWLER).mirror().orElse(DEFAULT_MIRROR);
        rawText = new LocalMirrorDownloader(mirror).rawText(bookId);
    }

    @Benchmark
    public BookText splitHeaderAndBody() {
        return GutenbergText.bookText(bookId, rawText);
    }
}
