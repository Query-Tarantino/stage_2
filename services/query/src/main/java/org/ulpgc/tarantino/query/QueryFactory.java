package org.ulpgc.tarantino.query;

import org.ulpgc.tarantino.query.adapters.index.folders.FolderPerTermIndexReader;
import org.ulpgc.tarantino.query.adapters.index.json.MonolithicJsonIndexReader;
import org.ulpgc.tarantino.query.adapters.index.mongo.MongodbIndexReader;
import org.ulpgc.tarantino.query.adapters.metadata.MongodbMetadataReader;
import org.ulpgc.tarantino.query.adapters.metadata.SqliteMetadataReader;
import org.ulpgc.tarantino.query.adapters.stopwords.FileStopwordsLoader;
import org.ulpgc.tarantino.query.commands.SearchCommand;
import org.ulpgc.tarantino.query.ports.InvertedIndexReader;
import org.ulpgc.tarantino.query.ports.MetadataReader;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class QueryFactory {

    private static final Map<String, Function<QueryConfig, InvertedIndexReader>> INDEX_STRUCTURES = Map.of(
            "json", config -> new MonolithicJsonIndexReader(config.datamarts().resolve("inverted_index.json")),
            "folders", config -> new FolderPerTermIndexReader(config.datamarts().resolve("inverted_index")),
            "mongo", config -> new MongodbIndexReader(config.mongoUri()));

    private static final Map<String, Function<QueryConfig, MetadataReader>> METADATA_BACKENDS = Map.of(
            "sqlite", config -> new SqliteMetadataReader(config.datamarts().resolve("metadata.db")),
            "mongo", config -> new MongodbMetadataReader(config.mongoUri()));

    private QueryFactory() {
    }

    public static SearchCommand searchCommand(QueryConfig config) {
        return new SearchCommand(invertedIndex(config), metadata(config), stopwords(config));
    }

    public static InvertedIndexReader invertedIndex(QueryConfig config) {
        return option(INDEX_STRUCTURES, config.index(), "index structure").apply(config);
    }

    public static MetadataReader metadata(QueryConfig config) {
        return option(METADATA_BACKENDS, config.metadata(), "metadata backend").apply(config);
    }

    private static Set<String> stopwords(QueryConfig config) {
        return new FileStopwordsLoader(config.workload().resolve("stopwords.txt")).stopwords();
    }

    private static <T> T option(Map<String, T> options, String name, String kind) {
        return Optional.ofNullable(options.get(name))
                .orElseThrow(() -> new IllegalArgumentException("Unknown " + kind + ": " + name));
    }
}
