package org.ulpgc.tarantino.common.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;

import static org.ulpgc.tarantino.common.mongo.MongoFields.GENERATION;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.SWITCHED_AT;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.NOW;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.ifNull;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;

public class IndexState {

    private static final int FIRST_GENERATION = 1;

    private final MongoCollection<Document> collection;

    public IndexState(MongoDatabase database) {
        this.collection = database.getCollection(MongoCollections.INDEX_STATE);
    }

    /** “Create the pointer” (SPEC §5.1): every service runs it at startup, and only the first one creates it. */
    public void createPointer() {
        collection.updateOne(Filters.eq(ID, MongoCollections.CURRENT),
                set(new Document(GENERATION, ifNull(GENERATION, FIRST_GENERATION)).append(SWITCHED_AT, ifNull(SWITCHED_AT, NOW))),
                new UpdateOptions().upsert(true));
    }
}
