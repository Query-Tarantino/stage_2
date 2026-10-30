package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.mongo.IndexState;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoFields;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.nowPlus;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;

class MongoOldGenerationsTest {

    private static final Duration GRACE_PERIOD = Duration.ofSeconds(600);
    private static final Duration LONG_AGO = Duration.ofHours(-1);

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void settlesTheCurrentGenerationOnlyAfterTheGracePeriod() {
        new IndexState(database()).createPointer();
        assertEquals(OptionalInt.empty(), generations().settledCurrent());

        database().getCollection(MongoCollections.INDEX_STATE).updateOne(Filters.eq(MongoFields.ID, MongoCollections.CURRENT),
                set(new Document(MongoFields.SWITCHED_AT, nowPlus(LONG_AGO))));

        assertEquals(OptionalInt.of(1), generations().settledCurrent());
    }

    @Test
    void findsTheGenerationsBelowWithAnyOfTheirCollections() {
        create(MongoCollections.postings(1), MongoCollections.batches(2), MongoCollections.metadata(3), MongoCollections.BOOK_STATES);

        assertEquals(List.of(1, 2), generations().existingBefore(3));
    }

    @Test
    void dropsEveryCollectionOfTheGenerationAndNothingElse() {
        create(MongoCollections.metadata(1), MongoCollections.postings(1), MongoCollections.batches(1),
                MongoCollections.postings(2), MongoCollections.BOOK_STATES);

        generations().drop(1);

        assertEquals(Set.of(MongoCollections.postings(2), MongoCollections.BOOK_STATES), collections());
    }

    private void create(String... collections) {
        List.of(collections).forEach(database()::createCollection);
    }

    private Set<String> collections() {
        return database().listCollectionNames().into(new HashSet<>());
    }

    private MongoOldGenerations generations() {
        return new MongoOldGenerations(database(), GRACE_PERIOD);
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }
}
