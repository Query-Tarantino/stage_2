package org.ulpgc.tarantino.crawler.benchmarking.datalake;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.ulpgc.tarantino.crawler.benchmarking.BenchmarkRunner;
import org.ulpgc.tarantino.crawler.benchmarking.support.datalake.DatalakeFixture;
import org.ulpgc.tarantino.crawler.benchmarking.support.dataset.BenchmarkDataset;
import org.ulpgc.tarantino.crawler.benchmarking.support.environment.BenchmarkPaths;
import org.ulpgc.tarantino.crawler.benchmarking.support.files.Directories;
import org.ulpgc.tarantino.crawler.benchmarking.support.files.FootprintLog;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
@Fork(value = 2, jvmArgsAppend = {"-Xms4g", "-Xmx4g", "--sun-misc-unsafe-memory-access=allow"})
public class DatalakeWriteBenchmark {

    @Param({"time", "book", "batch"})
    public String layout;

    @Param({"100", "300", "1000"})
    public int books;

    private final BenchmarkDataset dataset = BenchmarkDataset.fromEnvironment();
    private List<Integer> ids;
    private Path root;

    @Setup(Level.Trial)
    public void selectBooks() {
        ids = dataset.ids(books);
        root = BenchmarkPaths.scratch("datalake-write-" + layout + "-" + books);
    }

    @Setup(Level.Iteration)
    public void emptyDatalake() {
        Directories.delete(root);
    }

    @Benchmark
    public void writeThroughput() {
        DatalakeFixture.ingestAsCrawled(layout, root, ids, dataset, DatalakeFixture.CRAWL_START);
    }

    @TearDown(Level.Trial)
    public void recordFootprint() {
        FootprintLog.append(BenchmarkRunner.SERVICE, DatalakeFixture.footprint(layout, books, root));
        Directories.delete(root);
    }
}
