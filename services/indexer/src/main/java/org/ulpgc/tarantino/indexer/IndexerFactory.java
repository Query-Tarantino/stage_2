package org.ulpgc.tarantino.indexer;

import org.ulpgc.tarantino.indexer.adapters.datalake.batch.BatchBasedDatalakeReader;
import org.ulpgc.tarantino.indexer.adapters.datalake.book.BookBasedDatalakeReader;
import org.ulpgc.tarantino.indexer.adapters.datalake.time.TimeBasedDatalakeReader;
import org.ulpgc.tarantino.indexer.adapters.index.folders.FolderPerTermIndexAdapter;
import org.ulpgc.tarantino.indexer.adapters.index.json.MonolithicJsonIndexAdapter;
import org.ulpgc.tarantino.indexer.adapters.index.mongo.MongodbIndexAdapter;
import org.ulpgc.tarantino.indexer.adapters.metadata.MongodbMetadataAdapter;
import org.ulpgc.tarantino.indexer.adapters.metadata.SqliteMetadataAdapter;
import org.ulpgc.tarantino.indexer.adapters.stopwords.FileStopwordsLoader;
import org.ulpgc.tarantino.indexer.commands.IndexBookCommand;
import org.ulpgc.tarantino.indexer.model.book.HeaderParser;
import org.ulpgc.tarantino.indexer.model.terms.Tokenizer;
import org.ulpgc.tarantino.indexer.ports.datamarts.InvertedIndexStorage;
import org.ulpgc.tarantino.indexer.ports.datamarts.MetadataStorage;
import org.ulpgc.tarantino.indexer.ports.sources.DatalakeReader;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class IndexerFactory {

    private static final Map<String, Function<IndexerConfig, DatalakeReader>> DATALAKE_LAYOUTS = Map.of(
            "time", config -> new TimeBasedDatalakeReader(config.datalake()),
            "book", config -> new BookBasedDatalakeReader(config.datalake()),
            "batch", config -> new BatchBasedDatalakeReader(config.datalake()));

    private static final Map<String, Function<IndexerConfig, InvertedIndexStorage>> INDEX_STRUCTURES = Map.of(
            "json", config -> new MonolithicJsonIndexAdapter(config.datamarts().resolve("inverted_index.json")),
            "folders", config -> new FolderPerTermIndexAdapter(config.datamarts().resolve("inverted_index")),
            "mongo", config -> new MongodbIndexAdapter(config.mongoUri()));

    private static final Map<String, Function<IndexerConfig, MetadataStorage>> METADATA_BACKENDS = Map.of(
            "sqlite", config -> new SqliteMetadataAdapter(config.datamarts().resolve("metadata.db")),
            "mongo", config -> new MongodbMetadataAdapter(config.mongoUri()));

    private IndexerFactory() {
    }

    public static IndexBookCommand indexCommand(IndexerConfig config) {
        return new IndexBookCommand(datalakeReader(config), new HeaderParser(), tokenizer(config),
                invertedIndex(config), metadata(config));
    }

    public static DatalakeReader datalakeReader(IndexerConfig config) {
        return option(DATALAKE_LAYOUTS, config.datalakeLayout(), "datalake layout").apply(config);
    }

    public static InvertedIndexStorage invertedIndex(IndexerConfig config) {
        return option(INDEX_STRUCTURES, config.index(), "index structure").apply(config);
    }

    public static MetadataStorage metadata(IndexerConfig config) {
        return option(METADATA_BACKENDS, config.metadata(), "metadata backend").apply(config);
    }

    private static Tokenizer tokenizer(IndexerConfig config) {
        return new Tokenizer(new FileStopwordsLoader(config.workload().resolve("stopwords.txt")).stopwords());
    }

    private static <T> T option(Map<String, T> options, String name, String kind) {
        return Optional.ofNullable(options.get(name))
                .orElseThrow(() -> new IllegalArgumentException("Unknown " + kind + ": " + name));
    }
}
