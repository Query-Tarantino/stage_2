package org.ulpgc.tarantino.indexer.benchmarks;

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
import org.ulpgc.tarantino.common.terms.Tokenizer;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/** The term frequencies of a body, which the indexer computes for every book of a batch, on books of 40 KB, 450 KB and 3.4 MB. */
@State(Scope.Benchmark)
@Warmup(iterations = BenchmarkProtocol.WARMUP_ITERATIONS, time = TokenizerBenchmark.ITERATION_SECONDS)
@Measurement(iterations = BenchmarkProtocol.MEASUREMENT_ITERATIONS, time = TokenizerBenchmark.ITERATION_SECONDS)
@Fork(value = BenchmarkProtocol.FORKS, jvmArgs = BenchmarkProtocol.FIXED_HEAP)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class TokenizerBenchmark {

    static final int ITERATION_SECONDS = 5;

    @Param({"1080", "84", "2600"})
    public int bookId;

    private Tokenizer tokenizer;
    private String body;

    @Setup
    public void readTheBook() {
        tokenizer = BenchmarkBooks.tokenizer();
        body = BenchmarkBooks.book(bookId).body();
    }

    @Benchmark
    public Map<String, Integer> countTheTermsOfABody() {
        return tokenizer.frequencies(body);
    }
}
