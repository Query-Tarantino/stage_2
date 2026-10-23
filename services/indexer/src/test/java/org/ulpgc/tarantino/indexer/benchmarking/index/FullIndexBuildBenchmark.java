package org.ulpgc.tarantino.indexer.benchmarking.index;

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
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.runner.IterationType;
import org.ulpgc.tarantino.crawler.benchmarking.support.environment.Heap;
import org.ulpgc.tarantino.crawler.benchmarking.support.files.FootprintLog;
import org.ulpgc.tarantino.crawler.benchmarking.support.results.ResultRow;
import org.ulpgc.tarantino.crawler.benchmarking.support.validation.Check;
import org.ulpgc.tarantino.indexer.benchmarking.BenchmarkRunner;
import org.ulpgc.tarantino.indexer.benchmarking.support.BenchmarkStore;
import org.ulpgc.tarantino.indexer.benchmarking.support.IndexFixture;
import org.ulpgc.tarantino.indexer.benchmarking.support.PrebuiltIndexes;
import org.ulpgc.tarantino.indexer.benchmarking.support.StoreFootprint;
import org.ulpgc.tarantino.indexer.ports.datamarts.InvertedIndexStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.Reference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 3)
@Fork(value = 2, jvmArgsAppend = {"-Xms4g", "-Xmx4g", "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"})
public class FullIndexBuildBenchmark {

    private static final int WARM_UP_BOOKS = 100;
    // Adding without flushing is cheap next to a build, and 2 processes give 6 samples instead of 2
    private static final int BUILD_MEMORY_SAMPLES = 3;

    @Param({"json", "folders", "mongo"})
    public String index;

    @Param({"100", "300", "1000"})
    public int books;

    private IndexFixture fixture;
    private BenchmarkStore store;
    private List<Integer> ids;
    private long referenceTermCount;
    private boolean measured;
    private long allocated;

    @Setup(Level.Trial)
    public void selectBooks() {
        fixture = IndexFixture.fromEnvironment();
        store = BenchmarkStore.forIndex(index, "index-build-" + index + "-" + books);
        ids = fixture.dataset().ids(books);
        referenceTermCount = referenceTermCount();
        warmUp();
    }

    /**
     * Warms the JIT up with the same code on 100 books instead of a whole warm-up build of N (SPEC §11), and reads
     * the N texts once so that the first measured build finds them in the operating system cache, like the others.
     */
    private void warmUp() {
        ids.forEach(fixture.dataset()::rawText);
        BenchmarkStore warmUp = BenchmarkStore.forIndex(index, "index-build-warmup-" + index);
        warmUp.clear();
        fixture.index(warmUp.invertedIndex(), fixture.dataset().ids(WARM_UP_BOOKS));
        warmUp.clear();
    }

    @Setup(Level.Iteration)
    public void emptyIndex(IterationParams iteration) {
        store.clear();
        measured = iteration.getType() == IterationType.MEASUREMENT;
    }

    @Benchmark
    public void fullBuildTime() {
        long before = Heap.allocatedSoFar();
        fixture.index(store.invertedIndex(), ids);
        allocated = Heap.allocatedSoFar() - before;
    }

    // Measured here and not with JMH's GC profiler, which also counts iteration setup and trial teardown
    @TearDown(Level.Iteration)
    public void recordAllocations() {
        if (measured) {
            FootprintLog.appendSample(BenchmarkRunner.SERVICE, ResultRow.sample(index, "memory_allocated", books, allocated, "bytes"));
        }
    }

    @TearDown(Level.Trial)
    public void recordFootprint() {
        StoreFootprint footprint = store.footprint();
        Check.require(footprint.terms() == referenceTermCount,
                index + " holds " + footprint.terms() + " terms instead of the " + referenceTermCount + " of the books");
        FootprintLog.append(BenchmarkRunner.SERVICE, List.of(
                ResultRow.exact(index, "disk_usage", books, footprint.bytes(), "bytes"),
                ResultRow.exact(index, "disk_allocated", books, footprint.allocatedBytes(), "bytes"),
                ResultRow.exact(index, "term_count", books, footprint.terms(), "terms")));
        for (int sample = 0; sample < BUILD_MEMORY_SAMPLES; sample++) {
            store.clear();
            FootprintLog.appendSample(BenchmarkRunner.SERVICE, ResultRow.sample(index, "build_memory", books, buildMemory(), "bytes"));
        }
        store.clear();
    }

    private long referenceTermCount() {
        Path cached = PrebuiltIndexes.file("term-count-" + books + ".txt");
        try {
            if (Files.exists(cached)) {
                return Long.parseLong(Files.readString(cached).strip());
            }
            long termCount = fixture.vocabularySize(ids);
            Files.createDirectories(cached.getParent());
            Files.writeString(cached, String.valueOf(termCount));
            return termCount;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private long buildMemory() {
        long before = Heap.usedAfterFullCollection();
        InvertedIndexStorage invertedIndex = store.invertedIndex();
        fixture.add(invertedIndex, ids);
        long retained = Heap.usedAfterFullCollection() - before;
        Reference.reachabilityFence(invertedIndex);
        return retained;
    }
}
