package org.ulpgc.tarantino.indexer.benchmarking.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ulpgc.tarantino.crawler.benchmarking.support.environment.BenchmarkPaths;
import org.ulpgc.tarantino.crawler.benchmarking.support.files.Directories;
import org.ulpgc.tarantino.crawler.benchmarking.support.files.Footprint;
import org.ulpgc.tarantino.indexer.IndexerConfig;
import org.ulpgc.tarantino.indexer.IndexerFactory;
import org.ulpgc.tarantino.indexer.adapters.index.folders.TermFileRestore;
import org.ulpgc.tarantino.indexer.ports.datamarts.InvertedIndexStorage;
import org.ulpgc.tarantino.indexer.ports.datamarts.MetadataStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Set;

public final class BenchmarkStore {

    private static final String MONGO = "mongo";
    private static final String FOLDERS = "folders";
    private static final String INVERTED_INDEX = "inverted_index";

    private final IndexerConfig config;
    private final boolean mongo;

    private BenchmarkStore(IndexerConfig config, boolean mongo) {
        this.config = config;
        this.mongo = mongo;
    }

    public static BenchmarkStore forIndex(String index, String name) {
        return new BenchmarkStore(config(name, index, "sqlite"), MONGO.equals(index));
    }

    public static BenchmarkStore forMetadata(String metadata, String name) {
        return new BenchmarkStore(config(name, "json", metadata), MONGO.equals(metadata));
    }

    public InvertedIndexStorage invertedIndex() {
        return IndexerFactory.invertedIndex(config);
    }

    public MetadataStorage metadata() {
        return IndexerFactory.metadata(config);
    }

    public Path datamarts() {
        return config.datamarts();
    }

    public String mongoUri() {
        return config.mongoUri();
    }

    public void clear() {
        Directories.delete(config.datamarts());
        if (mongo) {
            MongoStores.drop(config.mongoUri());
        }
    }

    public void copyTo(BenchmarkStore target) {
        target.clear();
        Directories.copy(config.datamarts(), target.config.datamarts());
        if (mongo) {
            MongoStores.copy(config.mongoUri(), target.config.mongoUri());
        }
    }

    /**
     * Restores the snapshot after an update that only touched the given terms. For folders and MongoDB only their
     * files or documents are put back, instead of hundreds of thousands of them; json, a single file, is copied.
     */
    public void restoreTermsFrom(BenchmarkStore snapshot, Set<String> touchedTerms) {
        switch (config.index()) {
            case FOLDERS -> TermFileRestore.restore(snapshot.folderIndex(), folderIndex(), touchedTerms);
            case MONGO -> MongoStores.restoreDocuments(snapshot.config.mongoUri(), config.mongoUri(), INVERTED_INDEX, "term", touchedTerms);
            default -> snapshot.copyTo(this);
        }
    }

    /**
     * Measures the index once it is flushed. MongoDB reports allocated storage, so its two sizes are equal; a
     * folders index is walked once for its files, sizes and blocks.
     */
    public StoreFootprint footprint() {
        if (mongo) {
            long bytes = MongoStores.diskUsage(config.mongoUri());
            return new StoreFootprint(bytes, bytes, MongoStores.documentCount(config.mongoUri(), INVERTED_INDEX));
        }
        if (FOLDERS.equals(config.index())) {
            Footprint folders = Directories.footprint(folderIndex());
            return new StoreFootprint(folders.bytes(), folders.allocatedBytes(), folders.files());
        }
        Footprint json = Directories.footprint(config.datamarts());
        long terms = jsonTermCount(config.datamarts().resolve("inverted_index.json"));
        return new StoreFootprint(json.bytes(), json.allocatedBytes(), terms);
    }

    private Path folderIndex() {
        return config.datamarts().resolve("inverted_index");
    }

    private static long jsonTermCount(Path file) {
        try {
            return new ObjectMapper().readTree(file.toFile()).size();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static IndexerConfig config(String name, String index, String metadata) {
        return new IndexerConfig(Path.of("datalake"), "time", BenchmarkPaths.scratch(name), index, metadata,
                BenchmarkPaths.mongoUri(name.replace('-', '_')), BenchmarkPaths.workload());
    }
}
