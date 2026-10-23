package org.ulpgc.tarantino.query.benchmarking;

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
import org.ulpgc.tarantino.indexer.benchmarking.support.BenchmarkStore;
import org.ulpgc.tarantino.indexer.benchmarking.support.IndexFixture;
import org.ulpgc.tarantino.indexer.benchmarking.support.PrebuiltIndexes;
import org.ulpgc.tarantino.query.model.SearchResult;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
@Fork(value = 2, jvmArgsAppend = {"-Xms4g", "-Xmx4g", "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"})
public class IndexOpenBenchmark {

    @Param({"json", "folders", "mongo"})
    public String index;

    @Param({"100", "300", "1000"})
    public int books;

    private BenchmarkStore store;
    private Set<String> stopwords;
    private String firstQuery;

    @Setup(Level.Trial)
    public void selectIndex() {
        store = PrebuiltIndexes.of(index, books, IndexFixture.fromEnvironment());
        stopwords = QueryWorkload.stopwords();
        firstQuery = QueryWorkload.queries().getFirst().text();
    }

    @Benchmark
    public SearchResult indexOpenTime() {
        return QueryWorkload.openSearch(store, index, stopwords).execute(firstQuery);
    }
}
