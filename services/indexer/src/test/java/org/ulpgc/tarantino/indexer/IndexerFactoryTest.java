package org.ulpgc.tarantino.indexer;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.indexer.adapters.datalake.batch.BatchBasedDatalakeReader;
import org.ulpgc.tarantino.indexer.adapters.datalake.book.BookBasedDatalakeReader;
import org.ulpgc.tarantino.indexer.adapters.datalake.time.TimeBasedDatalakeReader;
import org.ulpgc.tarantino.indexer.adapters.index.folders.FolderPerTermIndexAdapter;
import org.ulpgc.tarantino.indexer.adapters.index.json.MonolithicJsonIndexAdapter;
import org.ulpgc.tarantino.indexer.adapters.metadata.SqliteMetadataAdapter;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IndexerFactoryTest {

    @Test
    void selectsTheConfiguredStructures() {
        assertInstanceOf(TimeBasedDatalakeReader.class, IndexerFactory.datalakeReader(config("time", "json", "sqlite")));
        assertInstanceOf(BookBasedDatalakeReader.class, IndexerFactory.datalakeReader(config("book", "json", "sqlite")));
        assertInstanceOf(BatchBasedDatalakeReader.class, IndexerFactory.datalakeReader(config("batch", "json", "sqlite")));
        assertInstanceOf(MonolithicJsonIndexAdapter.class, IndexerFactory.invertedIndex(config("time", "json", "sqlite")));
        assertInstanceOf(FolderPerTermIndexAdapter.class, IndexerFactory.invertedIndex(config("time", "folders", "sqlite")));
        assertInstanceOf(SqliteMetadataAdapter.class, IndexerFactory.metadata(config("time", "json", "sqlite")));
    }

    @Test
    void rejectsUnknownOptions() {
        assertThrows(IllegalArgumentException.class, () -> IndexerFactory.datalakeReader(config("hash", "json", "sqlite")));
        assertThrows(IllegalArgumentException.class, () -> IndexerFactory.invertedIndex(config("time", "trie", "sqlite")));
        assertThrows(IllegalArgumentException.class, () -> IndexerFactory.metadata(config("time", "json", "postgres")));
    }

    private static IndexerConfig config(String layout, String index, String metadata) {
        return new IndexerConfig(Path.of("datalake"), layout, Path.of("datamarts"), index, metadata,
                "mongodb://localhost:27017", Path.of("workload"));
    }
}
