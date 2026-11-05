package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.types.ObjectId;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.control.model.GenerationBatch;
import org.ulpgc.tarantino.control.ports.AbandonedBatches;

import java.time.Duration;
import java.util.List;

import static org.ulpgc.tarantino.common.mongo.MongoFields.ABANDONED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.BATCH_ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;

/** The postings of the batches abandoned longer than the grace period, step 5 of SPEC §5.3. */
public class MongoAbandonedBatches implements AbandonedBatches {

    private final MongoDatabase database;
    private final Duration gracePeriod;

    public MongoAbandonedBatches(MongoDatabase database, Duration gracePeriod) {
        this.database = database;
        this.gracePeriod = gracePeriod;
    }

    @Override
    public List<GenerationBatch> pastGracePeriod() {
        return ExistingGenerations.batchesMatching(database, Filters.and(
                Filters.eq(STATUS, BatchStatus.ABANDONED.name()), ServerTime.olderThan(ABANDONED_AT, gracePeriod)));
    }

    @Override
    public void deletePostings(GenerationBatch batch) {
        database.getCollection(MongoCollections.postings(batch.generation()))
                .deleteMany(Filters.eq(BATCH_ID, new ObjectId(batch.batchId())));
    }
}
