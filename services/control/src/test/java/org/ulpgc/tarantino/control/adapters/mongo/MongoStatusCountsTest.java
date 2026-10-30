package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.mongo.BookStates;
import org.ulpgc.tarantino.common.mongo.IndexState;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoFields;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;

class MongoStatusCountsTest {

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void countsTheBooksInEachStateThatHasAny() {
        List.of(1342, 84, 11).forEach(new BookStates(database())::create);
        database().getCollection(MongoCollections.BOOK_STATES).updateOne(Filters.eq(MongoFields.ID, 11),
                set(new Document(MongoFields.STATUS, BookStatus.PUBLISHED.name())));

        assertEquals(Map.of(BookStatus.QUEUED, 2L, BookStatus.PUBLISHED, 1L), counts().booksByStatus());
    }

    @Test
    void readsTheCurrentGeneration() {
        new IndexState(database()).createPointer();

        assertEquals(1, counts().currentGeneration());
    }

    private MongoStatusCounts counts() {
        return new MongoStatusCounts(database());
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }
}
