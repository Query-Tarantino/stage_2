package org.ulpgc.tarantino.query.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.mongo.IndexState;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.query.ports.PublishedBatches;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.ulpgc.tarantino.common.mongo.MongoFields.BOOK_IDS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;

public class MongoPublishedBatches implements PublishedBatches {

    private static final Bson PUBLISHED = Filters.eq(STATUS, BatchStatus.PUBLISHED.name());

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
    public List<String> publishedBatchIds(int generation) {
        return batches(generation).find(PUBLISHED)
                .projection(Projections.include(ID))
                .map(batch -> batch.getObjectId(ID).toHexString())
                .into(new ArrayList<>());
    }

    @Override
    public Map<String, List<Integer>> bookIds(int generation, Collection<String> batchIds) {
        Map<String, List<Integer>> books = new HashMap<>();
        batches(generation).find(Filters.and(Filters.in(ID, batchIds.stream().map(ObjectId::new).toList()), PUBLISHED))
                .projection(Projections.include(BOOK_IDS))
                .forEach(batch -> books.put(batch.getObjectId(ID).toHexString(), batch.getList(BOOK_IDS, Integer.class)));
        return books;
    }

    private MongoCollection<Document> batches(int generation) {
        return database.getCollection(MongoCollections.batches(generation));
    }
}
