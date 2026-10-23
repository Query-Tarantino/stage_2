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
import org.openjdk.jmh.annotations.Warmup;
import org.ulpgc.tarantino.crawler.benchmarking.support.dataset.BenchmarkDataset;
import org.ulpgc.tarantino.indexer.benchmarking.support.BenchmarkStore;
import org.ulpgc.tarantino.indexer.benchmarking.support.IndexFixture;
import org.ulpgc.tarantino.indexer.benchmarking.support.PrebuiltIndexes;
import org.ulpgc.tarantino.indexer.ports.datamarts.InvertedIndexStorage;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 3)
@Fork(value = 2, jvmArgsAppend = {"-Xms4g", "-Xmx4g", "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"})
public class IncrementalUpdateBenchmark {

    // Flushing after every book is far slower (folders rewrites every term file of each book), so fewer books
    public static final int BOOKS_FLUSHED_ONE_BY_ONE = 10;
    public static final int BOOKS_FLUSHED_TOGETHER = BenchmarkDataset.NEW_BOOKS;

    @Param({"json", "folders", "mongo"})
    public String index;

    @Param({"100", "300", "1000"})
    public int books;

    private IndexFixture fixture;
    private BenchmarkStore snapshot;
    private BenchmarkStore store;
    private List<Integer> newIds;
    private List<Integer> booksFlushedOneByOne;
    private Set<String> touchedTerms;
    private InvertedIndexStorage invertedIndex;

    @Setup(Level.Trial)
    public void selectSnapshot() {
        fixture = IndexFixture.fromEnvironment();
        snapshot = PrebuiltIndexes.of(index, books, fixture);
        store = PrebuiltIndexes.workingCopyOf(snapshot, index, books);
        newIds = fixture.dataset().newIds();
        booksFlushedOneByOne = newIds.subList(0, BOOKS_FLUSHED_ONE_BY_ONE);
        touchedTerms = newIds.stream().flatMap(bookId -> fixture.terms(bookId).stream()).collect(Collectors.toSet());
        warmUp();
    }

    /**
     * Warms the JIT up with the same code instead of a whole warm-up run (SPEC §11): the first new books indexed
     * into an empty index one by one, which creates their term files, and then together, which merges them.
     */
    private void warmUp() {
        BenchmarkStore warmUp = BenchmarkStore.forIndex(index, "index-update-warmup-" + index);
        warmUp.clear();
        InvertedIndexStorage invertedIndex = warmUp.invertedIndex();
        booksFlushedOneByOne.forEach(bookId -> fixture.index(invertedIndex, List.of(bookId)));
        fixture.index(warmUp.invertedIndex(), booksFlushedOneByOne);
        warmUp.clear();
    }

    /**
     * Puts back the terms of the new books right before every run, so each one starts from the prebuilt index
     * just written (SPEC §11), and opens the index before timing, as a running control layer has it open:
     * otherwise json's loading would be spread over 10 books in one method and over 100 in the other. Loading is
     * measured by index_open_time.
     */
    @Setup(Level.Iteration)
    public void restoreAndOpenIndex() {
        store.restoreTermsFrom(snapshot, touchedTerms);
        invertedIndex = store.invertedIndex();
        invertedIndex.open();
    }

    /** As the control layer indexes with K = 1 (SPEC §9): an open index, flushed after every book. */
    @Benchmark
    public void incrementalUpdateTime() {
        booksFlushedOneByOne.forEach(bookId -> fixture.index(invertedIndex, List.of(bookId)));
    }

    /** As the control layer indexes with the default K = 100: an open index, flushed once. */
    @Benchmark
    public void batchUpdateTime() {
        fixture.index(invertedIndex, newIds);
    }

}
