package org.ulpgc.tarantino.indexer.benchmarks;

import com.mongodb.client.MongoDatabase;
import org.bson.types.ObjectId;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoIndexes;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;
import org.ulpgc.tarantino.indexer.adapters.mongo.MongoIndexWriter;
import org.ulpgc.tarantino.indexer.commands.BookReader;
import org.ulpgc.tarantino.indexer.commands.IndexBatchCommand;
import org.ulpgc.tarantino.indexer.model.batch.Batch;
import org.ulpgc.tarantino.indexer.model.book.BookMetadata;
import org.ulpgc.tarantino.indexer.model.book.BookText;
import org.ulpgc.tarantino.indexer.model.book.ParsedBook;
import org.ulpgc.tarantino.indexer.model.terms.BatchPostings;
import org.ulpgc.tarantino.indexer.model.terms.TermPostings;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Gatherers;

/**
 * Writing a batch's metadata and postings to MongoDB, as the indexer does between opening and publishing it (SPEC §5.1),
 * for batches of 1, 10 and 100 books of the corpus. Each write is a new batch, and the generation is emptied before every
 * iteration so that it does not keep growing.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class BatchWriteBenchmark {

    private static final int GENERATION = 1;
    private static final String CLAIM_ID = "benchmark";

    @Param({"1", "10", "100"})
    public int books;

    private MongoDatabase database;
    private MongoIndexWriter writer;
    private List<BookMetadata> metadata;
    private List<List<TermPostings>> postingsChunks;

    @Setup(Level.Trial)
    public void parseTheBatch() {
        database = MongoDatabases.database(new TemporaryMongoDatabase().uri());
        writer = new MongoIndexWriter(database);
        Map<Integer, BookText> texts = BenchmarkBooks.firstBooks(books).stream()
                .collect(Collectors.toMap(BookText::bookId, Function.identity()));
        BookReader reader = new BookReader(bookId -> Optional.ofNullable(texts.get(bookId)), BenchmarkBooks.tokenizer());
        List<ParsedBook> parsed = texts.keySet().stream().map(reader::read).flatMap(Optional::stream).toList();
        metadata = parsed.stream().map(ParsedBook::metadata).toList();
        postingsChunks = BatchPostings.of(parsed.stream()
                        .collect(Collectors.toMap(book -> book.metadata().bookId(), ParsedBook::frequencies)))
                .stream()
                .gather(Gatherers.windowFixed(IndexBatchCommand.POSTINGS_PER_WRITE))
                .toList();
    }

    @Setup(Level.Iteration)
    public void emptyTheGeneration() {
        database.getCollection(MongoCollections.metadata(GENERATION)).drop();
        database.getCollection(MongoCollections.postings(GENERATION)).drop();
        MongoIndexes.ensureGenerationIndexes(database, GENERATION);
    }

    @Benchmark
    public void writeABatch() {
        Batch batch = new Batch(new ObjectId().toHexString(), CLAIM_ID, GENERATION);
        writer.writeMetadata(GENERATION, metadata);
        postingsChunks.forEach(chunk -> writer.writePostings(batch, chunk));
    }

    @TearDown(Level.Trial)
    public void dropTheDatabase() {
        database.drop();
    }
}
