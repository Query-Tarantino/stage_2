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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.ulpgc.tarantino.crawler.benchmarking.support.environment.BenchmarkPaths;
import org.ulpgc.tarantino.crawler.benchmarking.support.validation.Check;
import org.ulpgc.tarantino.indexer.benchmarking.support.BenchmarkStore;
import org.ulpgc.tarantino.indexer.benchmarking.support.IndexFixture;
import org.ulpgc.tarantino.indexer.model.book.Book;
import org.ulpgc.tarantino.query.QueryConfig;
import org.ulpgc.tarantino.query.QueryFactory;
import org.ulpgc.tarantino.query.model.BookMetadata;
import org.ulpgc.tarantino.query.ports.MetadataReader;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms4g", "-Xmx4g", "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"})
public class MetadataQueryBenchmark {

    @Param({"sqlite", "mongo"})
    public String metadata;

    @Param({"100", "300", "1000"})
    public int books;

    private BenchmarkStore store;
    private MetadataReader reader;
    private List<Integer> ids;
    private List<String> authors;

    @Setup(Level.Trial)
    public void saveMetadata() {
        store = BenchmarkStore.forMetadata(metadata, "query-metadata-" + metadata + "-" + books);
        store.clear();
        IndexFixture fixture = IndexFixture.fromEnvironment();
        ids = fixture.dataset().ids(books);
        List<Book> parsedBooks = fixture.books(ids);
        fixture.save(store.metadata(), parsedBooks);
        authors = parsedBooks.stream().map(Book::author).filter(Objects::nonNull).distinct().toList();
        reader = QueryFactory.metadata(config());
        ids.forEach(this::requireBook);
        parsedBooks.stream().filter(book -> book.author() != null).forEach(this::requireAmongBooksOfItsAuthor);
    }

    @Benchmark
    public Optional<BookMetadata> bookByIdTime() {
        return reader.book(ids.get(ThreadLocalRandom.current().nextInt(ids.size())));
    }

    @Benchmark
    public List<BookMetadata> booksByAuthorTime() {
        return reader.booksBy(authors.get(ThreadLocalRandom.current().nextInt(authors.size())));
    }

    @TearDown(Level.Trial)
    public void deleteMetadata() {
        store.clear();
    }

    private void requireBook(int bookId) {
        Check.require(reader.book(bookId).map(BookMetadata::bookId).equals(Optional.of(bookId)), "book " + bookId + " not found");
    }

    private void requireAmongBooksOfItsAuthor(Book book) {
        boolean found = reader.booksBy(book.author()).stream().anyMatch(candidate -> candidate.bookId() == book.bookId());
        Check.require(found, "book " + book.bookId() + " missing from the books of its author");
    }

    private QueryConfig config() {
        return new QueryConfig(store.datamarts(), "json", metadata, store.mongoUri(), BenchmarkPaths.workload());
    }
}
