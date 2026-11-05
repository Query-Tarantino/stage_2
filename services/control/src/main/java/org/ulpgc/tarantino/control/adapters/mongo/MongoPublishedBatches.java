package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.mongo.IndexState;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.control.ports.PublishedBatches;

import java.util.HashSet;
import java.util.Set;

import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;

/** {@code current.generation} and the {@code _id} of the {@code PUBLISHED} batches of {@code batches_g<n>} (SPEC §4.3). */
public class MongoPublishedBatches implements PublishedBatches {

    private final MongoDatabase database;
    private final IndexState indexState;

    public MongoPublishedBatches(MongoDatabase database) {
        this.database = database;
        this.indexState = new IndexState(database);
    }

    @Override
    public int currentGeneration() {
        return indexState.currentGeneration();
    }

    @Override
    public Set<String> publishedBatchIds(int generation) {
        return database.getCollection(MongoCollections.batches(generation))
                .find(Filters.eq(STATUS, BatchStatus.PUBLISHED.name()))
                .projection(Projections.include(ID))
                .map(batch -> batch.getObjectId(ID).toHexString())
                .into(new HashSet<>());
    }
}
