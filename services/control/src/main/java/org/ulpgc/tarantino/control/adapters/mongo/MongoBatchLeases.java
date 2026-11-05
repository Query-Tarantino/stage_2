package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.control.model.GenerationBatch;
import org.ulpgc.tarantino.control.ports.BatchLeases;

import java.util.List;

import static org.ulpgc.tarantino.common.mongo.MongoFields.ABANDONED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.LEASE_UNTIL;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.NOW;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.setThenUnset;

/** “Abandon a batch” (SPEC §5.1) over the expired batches of every generation, step 2 of SPEC §5.3. */
public class MongoBatchLeases implements BatchLeases {

    private final MongoDatabase database;

    public MongoBatchLeases(MongoDatabase database) {
        this.database = database;
    }

    @Override
    public List<GenerationBatch> expired() {
        return ExistingGenerations.batchesMatching(database, writingWithLeasePassed());
    }

    @Override
    public boolean abandon(GenerationBatch batch) {
        Bson expiredBatch = Filters.and(Filters.eq(ID, new ObjectId(batch.batchId())), writingWithLeasePassed());
        return batches(batch.generation()).updateOne(expiredBatch, abandonPipeline()).getModifiedCount() == 1;
    }

    private static List<Document> abandonPipeline() {
        return setThenUnset(new Document(STATUS, BatchStatus.ABANDONED.name()).append(ABANDONED_AT, NOW), LEASE_UNTIL);
    }

    private static Bson writingWithLeasePassed() {
        return Filters.and(Filters.eq(STATUS, BatchStatus.WRITING.name()), ServerTime.passed(LEASE_UNTIL));
    }

    private MongoCollection<Document> batches(int generation) {
        return database.getCollection(MongoCollections.batches(generation));
    }
}
