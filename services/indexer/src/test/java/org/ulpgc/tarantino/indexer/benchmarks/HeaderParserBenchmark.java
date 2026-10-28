package org.ulpgc.tarantino.indexer.benchmarks;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.ulpgc.tarantino.indexer.model.book.BookMetadata;
import org.ulpgc.tarantino.indexer.model.book.BookText;
import org.ulpgc.tarantino.indexer.model.book.HeaderParser;

import java.util.concurrent.TimeUnit;

/** The metadata of a header (SPEC §4.3), on the same three books as {@link TokenizerBenchmark}. */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class HeaderParserBenchmark {

    @Param({"1080", "84", "2600"})
    public int bookId;

    private HeaderParser headerParser;
    private BookText book;

    @Setup
    public void readTheBook() {
        headerParser = new HeaderParser(BenchmarkBooks.tokenizer());
        book = BenchmarkBooks.book(bookId);
    }

    @Benchmark
    public BookMetadata parseTheHeader() {
        return headerParser.metadata(book);
    }
}
