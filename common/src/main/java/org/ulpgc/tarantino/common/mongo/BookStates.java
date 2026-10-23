package org.ulpgc.tarantino.common.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.ulpgc.tarantino.common.model.BookErrorCode;
import org.ulpgc.tarantino.common.model.BookState;
import org.ulpgc.tarantino.common.model.BookStatus;

import java.util.Optional;

import static org.ulpgc.tarantino.common.mongo.MongoFields.ATTEMPTS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ENQUEUED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.LAST_ERROR;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.UPDATED_AT;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.NOW;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.ifNull;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;

public class BookStates {

    private final MongoCollection<Document> collection;

    public BookStates(MongoDatabase database) {
        this.collection = database.getCollection(MongoCollections.BOOK_STATES);
    }

    /** “Create the state” (SPEC §5.1): true only for the call that created it, which is the only one that enqueues it. */
    public boolean create(int bookId) {
        Document fields = new Document(STATUS, ifNull(STATUS, BookStatus.QUEUED.name()))
                .append(ATTEMPTS, ifNull(ATTEMPTS, 0))
                .append(ENQUEUED_AT, ifNull(ENQUEUED_AT, NOW))
                .append(UPDATED_AT, ifNull(UPDATED_AT, NOW));
        return collection.updateOne(Filters.eq(ID, bookId), set(fields), new UpdateOptions().upsert(true)).getUpsertedId() != null;
    }

    /** The state that {@code /ingest/status} and {@code /index/status} return (SPEC §6.2), if the book has a document. */
    public Optional<BookState> state(int bookId) {
        return Optional.ofNullable(collection.find(Filters.eq(ID, bookId))
                        .projection(Projections.include(STATUS, ATTEMPTS, LAST_ERROR, UPDATED_AT))
                        .first())
                .map(document -> new BookState(bookId,
                        BookStatus.valueOf(document.getString(STATUS)),
                        document.getInteger(ATTEMPTS),
                        Optional.ofNullable(document.getString(LAST_ERROR)).map(BookErrorCode::valueOf).orElse(null),
                        document.getDate(UPDATED_AT).toInstant()));
    }
}
