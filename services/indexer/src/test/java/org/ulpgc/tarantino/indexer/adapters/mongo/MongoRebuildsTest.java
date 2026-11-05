package org.ulpgc.tarantino.indexer.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.model.RebuildStatus;
import org.ulpgc.tarantino.common.mongo.IndexState;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoFields;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;
import org.ulpgc.tarantino.common.mongo.TwoAtOnce;
import org.ulpgc.tarantino.indexer.model.rebuild.Rebuild;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoRebuildsTest {

    private static final Rebuild OF_THE_SECOND_GENERATION = new Rebuild(1, 2, RebuildStatus.RUNNING);

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void opensARebuildOfTheGenerationAfterTheCurrentOne() {
        new IndexState(database()).createPointer();

        assertEquals(OF_THE_SECOND_GENERATION, new MongoRebuilds(database()).open());

        Document rebuild = rebuildDocument();
        assertEquals(2, rebuild.getInteger(MongoFields.TARGET_GENERATION));
        assertEquals(RebuildStatus.RUNNING.name(), rebuild.getString(MongoFields.STATUS));
        assertNotNull(rebuild.getDate(MongoFields.REQUESTED_AT));
        assertTrue(rebuild.containsKey(MongoFields.SWITCHED_AT));
        assertNull(rebuild.get(MongoFields.SWITCHED_AT));
    }

    @Test
    void twoRequestsAtOnceOpenOneRebuildAndBothGetIt() throws Exception {
        new IndexState(database()).createPointer();
        MongoRebuilds rebuilds = new MongoRebuilds(database());

        List<Rebuild> answers = TwoAtOnce.call(rebuilds::open);

        assertEquals(List.of(OF_THE_SECOND_GENERATION, OF_THE_SECOND_GENERATION), answers);
        assertEquals(1, indexState().countDocuments(Filters.eq(MongoFields.ID, MongoCollections.REBUILD)));
    }

    @Test
    void opensANewRebuildOnceTheLastOneIsDone() {
        Date switchedAt = new Date();
        indexState().insertOne(new Document(MongoFields.ID, MongoCollections.CURRENT)
                .append(MongoFields.GENERATION, 2).append(MongoFields.SWITCHED_AT, switchedAt));
        indexState().insertOne(new Document(MongoFields.ID, MongoCollections.REBUILD)
                .append(MongoFields.TARGET_GENERATION, 2).append(MongoFields.STATUS, RebuildStatus.DONE.name())
                .append(MongoFields.REQUESTED_AT, switchedAt).append(MongoFields.SWITCHED_AT, switchedAt));

        assertEquals(new Rebuild(2, 3, RebuildStatus.RUNNING), new MongoRebuilds(database()).open());
    }

    private Document rebuildDocument() {
        return indexState().find(Filters.eq(MongoFields.ID, MongoCollections.REBUILD)).first();
    }

    private MongoCollection<Document> indexState() {
        return database().getCollection(MongoCollections.INDEX_STATE);
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }
}
