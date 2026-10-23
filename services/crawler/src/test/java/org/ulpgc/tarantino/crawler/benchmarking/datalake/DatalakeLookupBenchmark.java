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
import org.ulpgc.tarantino.crawler.benchmarking.support.datalake.DatalakeFixture;
import org.ulpgc.tarantino.crawler.benchmarking.support.dataset.BenchmarkDataset;
import org.ulpgc.tarantino.crawler.benchmarking.support.environment.BenchmarkPaths;
import org.ulpgc.tarantino.crawler.benchmarking.support.files.Directories;
import org.ulpgc.tarantino.crawler.benchmarking.support.validation.Check;
import org.ulpgc.tarantino.crawler.model.book.StoredPaths;
import org.ulpgc.tarantino.crawler.ports.DatalakeStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms4g", "-Xmx4g", "--sun-misc-unsafe-memory-access=allow"})
public class DatalakeLookupBenchmark {

    @Param({"time", "book", "batch"})
    public String layout;

    @Param({"100", "300", "1000"})
    public int books;

    private List<Integer> ids;
    private Path root;
    private DatalakeStorage datalake;

    @Setup(Level.Trial)
    public void storeBooks() {
        BenchmarkDataset dataset = BenchmarkDataset.fromEnvironment();
        ids = dataset.ids(books);
        root = BenchmarkPaths.scratch("datalake-lookup-" + layout + "-" + books);
        Directories.delete(root);
        DatalakeFixture.ingestAsCrawled(layout, root, ids, dataset, DatalakeFixture.CRAWL_START);
        datalake = DatalakeFixture.datalake(layout, root);
        ids.forEach(this::requireStored);
    }

    @Benchmark
    public Optional<StoredPaths> lookupTime() {
        return datalake.pathsOf(ids.get(ThreadLocalRandom.current().nextInt(ids.size())));
    }

    @TearDown(Level.Trial)
    public void deleteDatalake() {
        Directories.delete(root);
    }

    private void requireStored(int bookId) {
        Check.require(datalake.pathsOf(bookId).filter(DatalakeLookupBenchmark::bothExist).isPresent(), "book " + bookId + " not found");
    }

    private static boolean bothExist(StoredPaths paths) {
        return Files.exists(paths.header()) && Files.exists(paths.body());
    }
}
