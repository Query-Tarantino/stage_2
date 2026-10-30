package org.ulpgc.tarantino.bench.recovery;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.mongo.IndexState;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MongoRecoveryReadsTest {

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void readsEverySeededBookInOrder() {
        MongoDatabase database = database();
        database.getCollection(MongoCollections.BOOK_STATES).insertMany(List.of(
                new Document("_id", 1342).append("status", "PUBLISHED"),
                new Document("_id", 84).append("status", "QUEUED")));

        assertEquals(List.of(84, 1342), new MongoRecoveryReads(database).seededBooks());
    }

    @Test
    void findsTheBooksOfTwoPublishedBatchesIgnoringAbandonedOnes() {
        MongoDatabase database = database();
        database.getCollection(MongoCollections.batches(1)).insertMany(List.of(
                batch("PUBLISHED", List.of(84, 1342)),
                batch("PUBLISHED", List.of(1342, 2701)),
                batch("ABANDONED", List.of(84)),
                batch("WRITING", List.of(2701))));

        assertEquals(List.of(1342), new MongoRecoveryReads(database).booksInTwoPublishedBatches());
    }

    @Test
    void countsOnlyThePostingsOfAbandonedBatches() {
        MongoDatabase database = database();
        Document abandoned = batch("ABANDONED", List.of());
        Document published = batch("PUBLISHED", List.of(84));
        database.getCollection(MongoCollections.batches(1)).insertMany(List.of(abandoned, published));
        database.getCollection(MongoCollections.postings(1)).insertMany(List.of(
                posting("monster", abandoned), posting("creator", abandoned), posting("monster", published)));

        assertEquals(2, new MongoRecoveryReads(database).postingsOfAbandonedBatches());
    }

    @Test
    void keepsTheTermsTheTextOfABookContainsInTheirOrder() {
        MongoDatabase database = database();
        Document published = batch("PUBLISHED", List.of(3, 84));
        database.getCollection(MongoCollections.postings(1)).insertMany(List.of(
                posting("inaugural", published, List.of(3)), posting("address", published, List.of(3, 84)),
                posting("kennedy", published, List.of(84))));

        assertEquals(List.of("inaugural", "address"),
                new MongoRecoveryReads(database).termsInText(3, List.of("john", "kennedy", "inaugural", "address")));
    }

    @Test
    void hasNoAbandonedPostingsWithoutAbandonedBatches() {
        assertEquals(0, new MongoRecoveryReads(database()).postingsOfAbandonedBatches());
    }

    private MongoDatabase database() {
        MongoDatabase database = MongoDatabases.database(mongo.uri());
        new IndexState(database).createPointer();
        return database;
    }

    private static Document batch(String status, List<Integer> bookIds) {
        return new Document("_id", new ObjectId()).append("status", status).append("book_ids", bookIds);
    }

    private static Document posting(String term, Document batch) {
        return posting(term, batch, List.of(84));
    }

    private static Document posting(String term, Document batch, List<Integer> bookIds) {
        return new Document("term", term).append("batch_id", batch.get("_id")).append("book_ids", bookIds)
                .append("tfs", bookIds.stream().map(bookId -> 1).toList());
    }
}
