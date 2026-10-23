package org.ulpgc.tarantino.indexer.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoFields;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;
import org.ulpgc.tarantino.indexer.model.batch.Batch;
import org.ulpgc.tarantino.indexer.model.book.BookMetadata;
import org.ulpgc.tarantino.indexer.model.terms.TermPostings;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoIndexWriterTest {

    private static final Batch FIRST = new Batch(new ObjectId().toHexString(), "claim", 1);
    private static final Batch SECOND = new Batch(new ObjectId().toHexString(), "claim", 1);

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void replacesTheMetadataOfABookWrittenAgain() {
        MongoIndexWriter writer = new MongoIndexWriter(database());
        writer.writeMetadata(1, List.of(metadata("Old title")));
        writer.writeMetadata(1, List.of(metadata("Pride and Prejudice")));

        MongoCollection<Document> metadata = database().getCollection(MongoCollections.metadata(1));
        Document book = metadata.find(Filters.eq(MongoFields.ID, 1342)).first();
        assertEquals(1, metadata.countDocuments());
        assertEquals("Pride and Prejudice", book.getString(MongoFields.TITLE));
        assertEquals(List.of("jane", "austen"), book.getList(MongoFields.AUTHOR_TERMS, String.class));
        assertEquals(List.of("en"), book.getList(MongoFields.LANGUAGES, String.class));
        assertEquals(1998, book.getInteger(MongoFields.YEAR));
        assertTrue(book.containsKey(MongoFields.ORIGINAL_YEAR));
        assertNull(book.get(MongoFields.ORIGINAL_YEAR));
    }

    @Test
    void keepsOnePostingPerTermAndBatch() {
        MongoIndexWriter writer = new MongoIndexWriter(database());
        writer.writePostings(FIRST, List.of(new TermPostings("pride", List.of(84, 1342), List.of(6, 55))));
        writer.writePostings(FIRST, List.of(new TermPostings("pride", List.of(1342), List.of(55))));
        writer.writePostings(SECOND, List.of(new TermPostings("pride", List.of(2600), List.of(30))));

        MongoCollection<Document> postings = database().getCollection(MongoCollections.postings(1));
        assertEquals(2, postings.countDocuments(Filters.eq(MongoFields.TERM, "pride")));
        Document first = postings.find(Filters.eq(MongoFields.BATCH_ID, new ObjectId(FIRST.batchId()))).first();
        assertEquals(List.of(1342), first.getList(MongoFields.BOOK_IDS, Integer.class));
        assertEquals(List.of(55), first.getList(MongoFields.TFS, Integer.class));
    }

    private static BookMetadata metadata(String title) {
        return new BookMetadata(1342, title, "Jane Austen", List.of("jane", "austen"), List.of("en"), List.of("English"), 1998, null);
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }
}
