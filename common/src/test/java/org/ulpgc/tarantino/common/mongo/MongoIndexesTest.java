package org.ulpgc.tarantino.common.mongo;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoIndexesTest {

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void ensuresTheStateIndexesAgainWithoutChanges() {
        MongoDatabase database = MongoDatabases.database(mongo.uri());

        MongoIndexes.ensureStateIndexes(database);
        MongoIndexes.ensureStateIndexes(database);

        assertEquals(Set.of("{_id: 1}", "{status: 1, enqueued_at: 1}", "{status: 1, lease_until: 1}", "{batch_id: 1}",
                "{generation: 1, status: 1}"), keys(database, MongoCollections.BOOK_STATES));
    }

    @Test
    void ensuresTheIndexesOfAGenerationAgainWithoutChanges() {
        MongoDatabase database = MongoDatabases.database(mongo.uri());

        MongoIndexes.ensureGenerationIndexes(database, 2);
        MongoIndexes.ensureGenerationIndexes(database, 2);

        assertEquals(Set.of("{_id: 1}", "{author_terms: 1}", "{languages: 1, year: 1}", "{year: 1}"),
                keys(database, MongoCollections.metadata(2)));
        assertEquals(Set.of("{_id: 1}", "{term: 1, batch_id: 1}", "{batch_id: 1}"), keys(database, MongoCollections.postings(2)));
        assertEquals(Set.of("{_id: 1}", "{status: 1, lease_until: 1}"), keys(database, MongoCollections.batches(2)));
        assertTrue(indexes(database, MongoCollections.postings(2)).stream()
                .anyMatch(index -> index.getBoolean("unique", false) && index.get("key", Document.class).containsKey("term")));
    }

    private static Set<String> keys(MongoDatabase database, String collection) {
        return indexes(database, collection).stream()
                .map(index -> index.get("key", Document.class).entrySet().stream()
                        .map(field -> field.getKey() + ": " + field.getValue())
                        .collect(Collectors.joining(", ", "{", "}")))
                .collect(Collectors.toSet());
    }

    private static List<Document> indexes(MongoDatabase database, String collection) {
        return database.getCollection(collection).listIndexes().into(new ArrayList<>());
    }
}
