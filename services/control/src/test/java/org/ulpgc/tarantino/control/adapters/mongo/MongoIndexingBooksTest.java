package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.mongo.BookStates;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoFields;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;
import org.ulpgc.tarantino.common.mongo.TwoAtOnce;
import org.ulpgc.tarantino.control.model.GenerationBatch;
import org.ulpgc.tarantino.control.model.IndexingBook;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.nowPlus;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;

class MongoIndexingBooksTest {

    private static final Duration LONG_AGO = Duration.ofHours(-1);

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void onlyOneOfTwoReleasesAtOnceMatches() throws Exception {
        IndexingBook book = indexing(1342, newBatch(2));
        Document before = state(1342);

        List<Boolean> outcomes = TwoAtOnce.call(() -> books().release(book));

        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        Document released = state(1342);
        assertEquals(BookStatus.DOWNLOADED.name(), released.getString(MongoFields.STATUS));
        assertFalse(released.containsKey(MongoFields.BATCH_ID));
        assertEquals(2, released.getInteger(MongoFields.GENERATION));
        assertTrue(released.getDate(MongoFields.ENQUEUED_AT).after(before.getDate(MongoFields.ENQUEUED_AT)));
    }

    @Test
    void onlyOneOfTwoCompletionsAtOnceMatches() throws Exception {
        IndexingBook book = indexing(1342, newBatch(2));

        List<Boolean> outcomes = TwoAtOnce.call(() -> books().complete(book));

        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        Document published = state(1342);
        assertEquals(BookStatus.PUBLISHED.name(), published.getString(MongoFields.STATUS));
        assertEquals(new ObjectId(book.batch().batchId()), published.getObjectId(MongoFields.BATCH_ID));
        assertTrue(published.containsKey(MongoFields.PUBLISHED_AT));
    }

    @Test
    void doesNotTouchABookThatAnotherBatchClaimedSince() {
        IndexingBook inOldBatch = indexing(1342, newBatch(1));
        indexing(1342, newBatch(1));

        assertFalse(books().release(inOldBatch));
        assertFalse(books().complete(inOldBatch));
        assertEquals(BookStatus.INDEXING.name(), state(1342).getString(MongoFields.STATUS));
    }

    @Test
    void findsOnlyTheIndexingBooksWithTheirBatch() {
        IndexingBook oldGeneration = indexing(1342, newBatch(1));
        IndexingBook currentGeneration = indexing(84, newBatch(2));
        new BookStates(database()).create(11);

        assertEquals(List.of(oldGeneration, currentGeneration), books().indexing());
    }

    @Test
    void readsTheStateOfTheBatchInItsGenerationOrNothingIfItIsMissing() {
        GenerationBatch published = newBatch(2);
        database().getCollection(MongoCollections.batches(2)).insertOne(new Document(MongoFields.ID, new ObjectId(published.batchId()))
                .append(MongoFields.STATUS, BatchStatus.PUBLISHED.name()));

        assertEquals(Optional.of(BatchStatus.PUBLISHED), books().statusOf(published));
        assertEquals(Optional.empty(), books().statusOf(new GenerationBatch(1, published.batchId())));
    }

    private static GenerationBatch newBatch(int generation) {
        return new GenerationBatch(generation, new ObjectId().toHexString());
    }

    private IndexingBook indexing(int bookId, GenerationBatch batch) {
        new BookStates(database()).create(bookId);
        states().updateOne(Filters.eq(MongoFields.ID, bookId), set(new Document(MongoFields.STATUS, BookStatus.INDEXING.name())
                .append(MongoFields.BATCH_ID, new ObjectId(batch.batchId()))
                .append(MongoFields.GENERATION, batch.generation())
                .append(MongoFields.ENQUEUED_AT, nowPlus(LONG_AGO))));
        return new IndexingBook(bookId, batch);
    }

    private MongoIndexingBooks books() {
        return new MongoIndexingBooks(database());
    }

    private Document state(int bookId) {
        return states().find(Filters.eq(MongoFields.ID, bookId)).first();
    }

    private MongoCollection<Document> states() {
        return database().getCollection(MongoCollections.BOOK_STATES);
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }
}
