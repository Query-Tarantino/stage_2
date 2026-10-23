package org.ulpgc.tarantino.common.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IndexStateTest {

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void twoServicesStartingAtOnceLeaveASinglePointerAtTheFirstGeneration() throws Exception {
        IndexState state = new IndexState(MongoDatabases.database(mongo.uri()));

        TwoAtOnce.call(() -> {
            state.createPointer();
            return null;
        });

        assertEquals(1, pointers().countDocuments());
        Document pointer = pointers().find().first();
        assertEquals(1, pointer.getInteger(MongoFields.GENERATION));
        assertNotNull(pointer.getDate(MongoFields.SWITCHED_AT));
    }

    @Test
    void keepsAnExistingPointer() {
        IndexState state = new IndexState(MongoDatabases.database(mongo.uri()));
        Date switchedAt = new Date(0);
        pointers().insertOne(new Document(MongoFields.ID, MongoCollections.CURRENT)
                .append(MongoFields.GENERATION, 3).append(MongoFields.SWITCHED_AT, switchedAt));

        state.createPointer();

        Document pointer = pointers().find(Filters.eq(MongoFields.ID, MongoCollections.CURRENT)).first();
        assertEquals(3, pointer.getInteger(MongoFields.GENERATION));
        assertEquals(switchedAt, pointer.getDate(MongoFields.SWITCHED_AT));
    }

    private MongoCollection<Document> pointers() {
        return MongoDatabases.database(mongo.uri()).getCollection(MongoCollections.INDEX_STATE);
    }
}
