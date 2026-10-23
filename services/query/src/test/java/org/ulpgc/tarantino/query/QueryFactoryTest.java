package org.ulpgc.tarantino.query;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.query.adapters.index.folders.FolderPerTermIndexReader;
import org.ulpgc.tarantino.query.adapters.index.json.MonolithicJsonIndexReader;
import org.ulpgc.tarantino.query.adapters.metadata.SqliteMetadataReader;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueryFactoryTest {

    @Test
    void selectsTheConfiguredStructures() {
        assertInstanceOf(MonolithicJsonIndexReader.class, QueryFactory.invertedIndex(config("json", "sqlite")));
        assertInstanceOf(FolderPerTermIndexReader.class, QueryFactory.invertedIndex(config("folders", "sqlite")));
        assertInstanceOf(SqliteMetadataReader.class, QueryFactory.metadata(config("json", "sqlite")));
    }

    @Test
    void rejectsUnknownOptions() {
        assertThrows(IllegalArgumentException.class, () -> QueryFactory.invertedIndex(config("trie", "sqlite")));
        assertThrows(IllegalArgumentException.class, () -> QueryFactory.metadata(config("json", "postgres")));
    }

    private static QueryConfig config(String index, String metadata) {
        return new QueryConfig(Path.of("datamarts"), index, metadata, "mongodb://localhost:27017", Path.of("workload"));
    }
}
