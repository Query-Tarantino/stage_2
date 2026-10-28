package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoFields;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;
import org.ulpgc.tarantino.control.model.GenerationBatch;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.nowPlus;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;

class MongoAbandonedBatchesTest {

    private static final Duration GRACE_PERIOD = Duration.ofSeconds(600);
    private static final Duration LONG_AGO = Duration.ofHours(-1);
    private static final Duration RECENTLY = Duration.ofSeconds(-1);

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void findsTheBatchesAbandonedPastTheGracePeriodInEveryGeneration() {
        GenerationBatch oldGeneration = batch(1, BatchStatus.ABANDONED, LONG_AGO);
        GenerationBatch currentGeneration = batch(2, BatchStatus.ABANDONED, LONG_AGO);
        batch(2, BatchStatus.ABANDONED, RECENTLY);
        batch(2, BatchStatus.PUBLISHED, LONG_AGO);

        assertEquals(List.of(oldGeneration, currentGeneration), batches().pastGracePeriod());
    }

    @Test
    void deletesOnlyThePostingsOfThatBatch() {
        GenerationBatch abandoned = batch(2, BatchStatus.ABANDONED, LONG_AGO);
        GenerationBatch published = batch(2, BatchStatus.PUBLISHED, LONG_AGO);
        posting(abandoned, "darcy");
        posting(published, "darcy");

        batches().deletePostings(abandoned);

        assertEquals(List.of(new ObjectId(published.batchId())), postingBatchIds(2));
    }

    @Test
    void deletesThePostingsAZombieIndexerWritesAfterTheFirstDeletion() {
        GenerationBatch abandoned = batch(2, BatchStatus.ABANDONED, LONG_AGO);
        posting(abandoned, "darcy");
        batches().deletePostings(abandoned);
        posting(abandoned, "bennet");

        batches().deletePostings(abandoned);

        assertEquals(List.of(), postingBatchIds(2));
    }

    private GenerationBatch batch(int generation, BatchStatus status, Duration reachedAgo) {
        ObjectId batchId = new ObjectId();
        String reachedAt = status == BatchStatus.ABANDONED ? MongoFields.ABANDONED_AT : MongoFields.PUBLISHED_AT;
        batches(generation).updateOne(Filters.eq(MongoFields.ID, batchId), set(new Document(MongoFields.STATUS, status.name())
                .append(reachedAt, nowPlus(reachedAgo))), new UpdateOptions().upsert(true));
        return new GenerationBatch(generation, batchId.toHexString());
    }

    private void posting(GenerationBatch batch, String term) {
        postings(batch.generation()).insertOne(new Document(MongoFields.TERM, term)
                .append(MongoFields.BATCH_ID, new ObjectId(batch.batchId()))
                .append(MongoFields.BOOK_IDS, List.of(1342))
                .append(MongoFields.TFS, List.of(3)));
    }

    private List<ObjectId> postingBatchIds(int generation) {
        return postings(generation).find().map(posting -> posting.getObjectId(MongoFields.BATCH_ID)).into(new ArrayList<>());
    }

    private MongoAbandonedBatches batches() {
        return new MongoAbandonedBatches(database(), GRACE_PERIOD);
    }

    private MongoCollection<Document> batches(int generation) {
        return database().getCollection(MongoCollections.batches(generation));
    }

    private MongoCollection<Document> postings(int generation) {
        return database().getCollection(MongoCollections.postings(generation));
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }
}
