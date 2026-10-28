package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoFields;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;
import org.ulpgc.tarantino.common.mongo.TwoAtOnce;
import org.ulpgc.tarantino.control.model.GenerationBatch;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.NOW;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.nowPlus;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.setThenUnset;

class MongoBatchLeasesTest {

    private static final Duration EXPIRED = Duration.ofSeconds(-1);
    private static final Duration ALIVE = Duration.ofSeconds(60);

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void onlyOneOfTwoAbandonsAtOnceMatches() throws Exception {
        GenerationBatch batch = opened(1, EXPIRED);

        List<Boolean> outcomes = TwoAtOnce.call(() -> leases().abandon(batch));

        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        Document abandoned = batch(batch);
        assertEquals(BatchStatus.ABANDONED.name(), abandoned.getString(MongoFields.STATUS));
        assertTrue(abandoned.containsKey(MongoFields.ABANDONED_AT));
        assertFalse(abandoned.containsKey(MongoFields.LEASE_UNTIL));
    }

    @Test
    void doesNotAbandonABatchWhoseLeaseIsStillAlive() {
        GenerationBatch batch = opened(1, ALIVE);

        assertFalse(leases().abandon(batch));
        assertEquals(BatchStatus.WRITING.name(), batch(batch).getString(MongoFields.STATUS));
    }

    @Test
    void doesNotAbandonABatchThatWasPublishedFirst() {
        GenerationBatch batch = opened(1, ALIVE);
        published(batch);

        assertFalse(leases().abandon(batch));
        assertEquals(BatchStatus.PUBLISHED.name(), batch(batch).getString(MongoFields.STATUS));
    }

    @Test
    void findsTheExpiredBatchesOfEveryGeneration() {
        GenerationBatch oldGeneration = opened(1, EXPIRED);
        GenerationBatch currentGeneration = opened(2, EXPIRED);
        opened(2, ALIVE);
        published(opened(1, ALIVE));
        database().createCollection(MongoCollections.postings(3));

        assertEquals(List.of(oldGeneration, currentGeneration), leases().expired());
    }

    private GenerationBatch opened(int generation, Duration leaseLeft) {
        ObjectId batchId = new ObjectId();
        batches(generation).updateOne(Filters.eq(MongoFields.ID, batchId), set(new Document(MongoFields.STATUS, BatchStatus.WRITING.name())
                .append(MongoFields.CLAIM_ID, "claim-" + batchId.toHexString())
                .append(MongoFields.OWNER, "indexer-1")
                .append(MongoFields.LEASE_UNTIL, nowPlus(leaseLeft))
                .append(MongoFields.BOOK_IDS, List.of())
                .append(MongoFields.OPENED_AT, NOW)), new UpdateOptions().upsert(true));
        return new GenerationBatch(generation, batchId.toHexString());
    }

    private void published(GenerationBatch batch) {
        batches(batch.generation()).updateOne(byId(batch), setThenUnset(new Document(MongoFields.STATUS, BatchStatus.PUBLISHED.name())
                .append(MongoFields.PUBLISHED_AT, NOW)
                .append(MongoFields.BOOK_IDS, List.of(1342)), MongoFields.LEASE_UNTIL));
    }

    private MongoBatchLeases leases() {
        return new MongoBatchLeases(database());
    }

    private Document batch(GenerationBatch batch) {
        return batches(batch.generation()).find(byId(batch)).first();
    }

    private static Bson byId(GenerationBatch batch) {
        return Filters.eq(MongoFields.ID, new ObjectId(batch.batchId()));
    }

    private MongoCollection<Document> batches(int generation) {
        return database().getCollection(MongoCollections.batches(generation));
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }
}
