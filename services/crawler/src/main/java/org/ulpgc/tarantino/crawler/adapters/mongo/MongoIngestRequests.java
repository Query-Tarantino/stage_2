package org.ulpgc.tarantino.crawler.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.mongo.BookStates;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.crawler.ports.IngestRequests;

import static org.ulpgc.tarantino.common.mongo.MongoFields.ATTEMPTS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ENQUEUED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.LAST_ERROR;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.UPDATED_AT;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.NOW;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;

/** “Create the state”, from {@code common}, and “Retry a {@code FAILED} book” (SPEC §5.1), each a single conditional write. */
public class MongoIngestRequests implements IngestRequests {

    private final BookStates bookStates;
    private final MongoCollection<Document> states;

    public MongoIngestRequests(MongoDatabase database) {
        this.bookStates = new BookStates(database);
        this.states = database.getCollection(MongoCollections.BOOK_STATES);
    }

    @Override
    public boolean create(int bookId) {
        return bookStates.create(bookId);
    }

    @Override
    public boolean retry(int bookId) {
        Document change = new Document(STATUS, BookStatus.QUEUED.name())
                .append(ATTEMPTS, 0)
                .append(LAST_ERROR, null)
                .append(ENQUEUED_AT, NOW)
                .append(UPDATED_AT, NOW);
        Bson failed = Filters.and(Filters.eq(ID, bookId), Filters.eq(STATUS, BookStatus.FAILED.name()));
        return states.updateOne(failed, set(change)).getMatchedCount() == 1;
    }
}
