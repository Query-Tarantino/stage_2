package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.control.ports.LostMessages;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.ulpgc.tarantino.common.mongo.MongoFields.ENQUEUED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.UPDATED_AT;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.NOW;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;

/** “Requeue a lost message” (SPEC §5.1) over the books that step 4 of SPEC §5.3 finds waiting too long. */
public class MongoLostMessages implements LostMessages {

    private final MongoCollection<Document> states;
    private final Duration requeue;

    public MongoLostMessages(MongoDatabase database, Duration requeue) {
        this.states = database.getCollection(MongoCollections.BOOK_STATES);
        this.requeue = requeue;
    }

    @Override
    public List<Integer> lost(BookStatus status) {
        return states.find(waitingTooLong(status))
                .projection(Projections.include(ID))
                .map(state -> state.getInteger(ID))
                .into(new ArrayList<>());
    }

    @Override
    public boolean requeue(int bookId, BookStatus status) {
        Bson lostBook = Filters.and(Filters.eq(ID, bookId), waitingTooLong(status));
        return states.updateOne(lostBook, set(new Document(ENQUEUED_AT, NOW).append(UPDATED_AT, NOW))).getModifiedCount() == 1;
    }

    private Bson waitingTooLong(BookStatus status) {
        return Filters.and(Filters.eq(STATUS, status.name()), ServerTime.olderThan(ENQUEUED_AT, requeue));
    }
}
