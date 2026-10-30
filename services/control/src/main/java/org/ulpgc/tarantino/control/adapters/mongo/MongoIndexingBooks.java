package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.control.model.GenerationBatch;
import org.ulpgc.tarantino.control.model.IndexingBook;
import org.ulpgc.tarantino.control.ports.IndexingBooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.ulpgc.tarantino.common.mongo.MongoFields.BATCH_ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ENQUEUED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.GENERATION;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.PUBLISHED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.UPDATED_AT;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.NOW;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.setThenUnset;

/** “Release its books” and “Complete a publication” (SPEC §5.1) over the {@code INDEXING} books of step 3 of SPEC §5.3. */
public class MongoIndexingBooks implements IndexingBooks {

    private final MongoDatabase database;
    private final MongoCollection<Document> states;

    public MongoIndexingBooks(MongoDatabase database) {
        this.database = database;
        this.states = database.getCollection(MongoCollections.BOOK_STATES);
    }

    @Override
    public List<IndexingBook> indexing() {
        return states.find(Filters.eq(STATUS, BookStatus.INDEXING.name()))
                .projection(Projections.include(BATCH_ID, GENERATION))
                .map(state -> new IndexingBook(state.getInteger(ID),
                        new GenerationBatch(state.getInteger(GENERATION), state.getObjectId(BATCH_ID).toHexString())))
                .into(new ArrayList<>());
    }

    @Override
    public Optional<BatchStatus> statusOf(GenerationBatch batch) {
        Document found = database.getCollection(MongoCollections.batches(batch.generation()))
                .find(Filters.eq(ID, new ObjectId(batch.batchId())))
                .projection(Projections.include(STATUS))
                .first();
        return Optional.ofNullable(found).map(document -> BatchStatus.valueOf(document.getString(STATUS)));
    }

    @Override
    public boolean release(IndexingBook book) {
        Document downloaded = new Document(STATUS, BookStatus.DOWNLOADED.name())
                .append(ENQUEUED_AT, NOW)
                .append(UPDATED_AT, NOW);
        return states.updateOne(inItsBatch(book), setThenUnset(downloaded, BATCH_ID)).getModifiedCount() == 1;
    }

    @Override
    public boolean complete(IndexingBook book) {
        Document published = new Document(STATUS, BookStatus.PUBLISHED.name())
                .append(PUBLISHED_AT, NOW)
                .append(UPDATED_AT, NOW);
        return states.updateOne(inItsBatch(book), set(published)).getModifiedCount() == 1;
    }

    private static Bson inItsBatch(IndexingBook book) {
        return Filters.and(Filters.eq(ID, book.bookId()), Filters.eq(STATUS, BookStatus.INDEXING.name()),
                Filters.eq(BATCH_ID, new ObjectId(book.batch().batchId())));
    }
}
