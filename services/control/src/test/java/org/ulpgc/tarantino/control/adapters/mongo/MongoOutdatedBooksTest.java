package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.bson.types.ObjectId;
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

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.NOW;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.nowPlus;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;

class MongoOutdatedBooksTest {

    private static final Duration LONG_AGO = Duration.ofHours(-1);

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void onlyOneOfTwoReindexesAtOnceMatches() throws Exception {
        published(1342, 1);
        Document before = state(1342);

        List<Boolean> outcomes = TwoAtOnce.call(() -> books().reindex(1342, 2));

        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        Document reindexed = state(1342);
        assertEquals(BookStatus.DOWNLOADED.name(), reindexed.getString(MongoFields.STATUS));
        assertFalse(reindexed.containsKey(MongoFields.BATCH_ID));
        assertEquals(1, reindexed.getInteger(MongoFields.GENERATION));
        assertTrue(reindexed.getDate(MongoFields.ENQUEUED_AT).after(before.getDate(MongoFields.ENQUEUED_AT)));
    }

    @Test
    void doesNotReindexABookAlreadyInTheWriteGeneration() {
        published(1342, 2);

        assertFalse(books().reindex(1342, 2));
        assertEquals(BookStatus.PUBLISHED.name(), state(1342).getString(MongoFields.STATUS));
    }

    @Test
    void findsOnlyThePublishedBooksOfOlderGenerations() {
        published(1342, 1);
        published(84, 2);
        new BookStates(database()).create(11);

        assertEquals(List.of(1342), books().outdated(2));
    }

    @Test
    void writesIntoTheTargetOfARunningRebuild() {
        new IndexState(database()).createPointer();
        assertEquals(1, books().writeGeneration());

        indexState().updateOne(Filters.eq(MongoFields.ID, MongoCollections.REBUILD), set(new Document(MongoFields.TARGET_GENERATION, 2)
                .append(MongoFields.STATUS, RebuildStatus.RUNNING.name())
                .append(MongoFields.REQUESTED_AT, NOW)
                .append(MongoFields.SWITCHED_AT, null)), new UpdateOptions().upsert(true));

        assertEquals(2, books().writeGeneration());
    }

    private void published(int bookId, int generation) {
        new BookStates(database()).create(bookId);
        states().updateOne(Filters.eq(MongoFields.ID, bookId), set(new Document(MongoFields.STATUS, BookStatus.PUBLISHED.name())
                .append(MongoFields.BATCH_ID, new ObjectId())
                .append(MongoFields.GENERATION, generation)
                .append(MongoFields.ENQUEUED_AT, nowPlus(LONG_AGO))));
    }

    private MongoOutdatedBooks books() {
        return new MongoOutdatedBooks(database());
    }

    private Document state(int bookId) {
        return states().find(Filters.eq(MongoFields.ID, bookId)).first();
    }

    private MongoCollection<Document> states() {
        return database().getCollection(MongoCollections.BOOK_STATES);
    }

    private MongoCollection<Document> indexState() {
        return database().getCollection(MongoCollections.INDEX_STATE);
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }
}
