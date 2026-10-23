package org.ulpgc.tarantino.indexer.adapters.mongo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MongoDatabasesTest {

    @Test
    void useTheUriDatabaseOrDefaultToTarantino() {
        assertEquals("tarantino", MongoDatabases.database("mongodb://localhost:27017").getName());
        assertEquals("tarantino_benchmark", MongoDatabases.database("mongodb://localhost:27017/tarantino_benchmark").getName());
    }
}
