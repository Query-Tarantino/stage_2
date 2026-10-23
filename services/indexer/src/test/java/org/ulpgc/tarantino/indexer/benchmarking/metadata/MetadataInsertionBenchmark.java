package org.ulpgc.tarantino.indexer.benchmarking.metadata;

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
import org.ulpgc.tarantino.indexer.benchmarking.support.BenchmarkStore;
import org.ulpgc.tarantino.indexer.benchmarking.support.IndexFixture;
import org.ulpgc.tarantino.indexer.model.book.Book;

import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
@Fork(value = 2, jvmArgsAppend = {"-Xms4g", "-Xmx4g", "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"})
public class MetadataInsertionBenchmark {

    @Param({"sqlite", "mongo"})
    public String metadata;

    @Param({"100", "300", "1000"})
    public int books;

    private IndexFixture fixture;
    private BenchmarkStore store;
    private List<Book> parsedBooks;

    @Setup(Level.Trial)
    public void parseHeaders() {
        fixture = IndexFixture.fromEnvironment();
        store = BenchmarkStore.forMetadata(metadata, "metadata-insert-" + metadata + "-" + books);
        parsedBooks = fixture.books(fixture.dataset().ids(books));
    }

    @Setup(Level.Iteration)
    public void emptyMetadata() {
        store.clear();
    }

    @Benchmark
    public void bulkInsertionTime() {
        fixture.save(store.metadata(), parsedBooks);
    }

    @TearDown(Level.Trial)
    public void deleteMetadata() {
        store.clear();
    }
}
