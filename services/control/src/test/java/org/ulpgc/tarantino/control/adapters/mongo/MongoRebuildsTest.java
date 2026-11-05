package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.RebuildStatus;
import org.ulpgc.tarantino.common.mongo.BookStates;
import org.ulpgc.tarantino.common.mongo.IndexState;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoFields;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;
import org.ulpgc.tarantino.common.mongo.TwoAtOnce;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.NOW;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;

class MongoRebuildsTest {

    private static final int TARGET = 2;

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @BeforeEach
    void runARebuildFromTheFirstGeneration() {
        new IndexState(database()).createPointer();
        pointers().updateOne(Filters.eq(MongoFields.ID, MongoCollections.REBUILD), set(new Document(MongoFields.TARGET_GENERATION, TARGET)
                .append(MongoFields.STATUS, RebuildStatus.RUNNING.name())
                .append(MongoFields.REQUESTED_AT, NOW)
                .append(MongoFields.SWITCHED_AT, null)), new UpdateOptions().upsert(true));
    }

    @Test
    void onlyOneOfTwoSwitchesAtOnceMatches() throws Exception {
        List<Boolean> outcomes = TwoAtOnce.call(() -> rebuilds().switchTo(TARGET));

        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(TARGET, rebuilds().currentGeneration());
    }

    @Test
    void onlyOneOfTwoFinishesAtOnceMatches() throws Exception {
        List<Boolean> outcomes = TwoAtOnce.call(() -> rebuilds().finish(TARGET));

        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        Document rebuild = pointer(MongoCollections.REBUILD);
        assertEquals(RebuildStatus.DONE.name(), rebuild.getString(MongoFields.STATUS));
        assertNotNull(rebuild.getDate(MongoFields.SWITCHED_AT));
        assertEquals(OptionalInt.empty(), rebuilds().runningTarget());
    }

    @Test
    void findsTheTargetOfTheRunningRebuild() {
        assertEquals(OptionalInt.of(TARGET), rebuilds().runningTarget());
    }

    @Test
    void isReadyWhenOnlyFailedBooksAndBooksNeverIndexedAreLeftBelowTheTarget() {
        book(1342, BookStatus.PUBLISHED, TARGET);
        book(84, BookStatus.FAILED, 1);
        new BookStates(database()).create(11);

        assertTrue(rebuilds().readyToSwitch(TARGET));
    }

    @Test
    void isNotReadyWhileABookOfAnOlderGenerationIsLeft() {
        book(1342, BookStatus.DOWNLOADED, 1);

        assertFalse(rebuilds().readyToSwitch(TARGET));
    }

    @Test
    void isNotReadyWhileABookOfTheTargetIsBeingIndexed() {
        book(1342, BookStatus.INDEXING, TARGET);

        assertFalse(rebuilds().readyToSwitch(TARGET));
    }

    private void book(int bookId, BookStatus status, int generation) {
        new BookStates(database()).create(bookId);
        database().getCollection(MongoCollections.BOOK_STATES).updateOne(Filters.eq(MongoFields.ID, bookId),
                set(new Document(MongoFields.STATUS, status.name()).append(MongoFields.GENERATION, generation)));
    }

    private MongoRebuilds rebuilds() {
        return new MongoRebuilds(database());
    }

    private Document pointer(String id) {
        return pointers().find(Filters.eq(MongoFields.ID, id)).first();
    }

    private MongoCollection<Document> pointers() {
        return database().getCollection(MongoCollections.INDEX_STATE);
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }
}
