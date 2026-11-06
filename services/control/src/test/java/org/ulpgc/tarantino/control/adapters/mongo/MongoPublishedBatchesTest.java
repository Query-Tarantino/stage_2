package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.mongo.IndexState;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoFields;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MongoPublishedBatchesTest {

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void readsTheCurrentGenerationFromThePointer() {
        new IndexState(database()).createPointer();

        assertEquals(IndexState.FIRST_GENERATION, new MongoPublishedBatches(database()).currentGeneration());
    }

    @Test
    void returnsOnlyThePublishedBatchesOfTheGeneration() {
        String published = batch(2, BatchStatus.PUBLISHED);
        batch(2, BatchStatus.WRITING);
        batch(2, BatchStatus.ABANDONED);
        batch(1, BatchStatus.PUBLISHED);

        assertEquals(Set.of(published), new MongoPublishedBatches(database()).publishedBatchIds(2));
    }

    private String batch(int generation, BatchStatus status) {
        ObjectId batchId = new ObjectId();
        database().getCollection(MongoCollections.batches(generation))
                .insertOne(new Document(MongoFields.ID, batchId).append(MongoFields.STATUS, status.name()));
        return batchId.toHexString();
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }
}
