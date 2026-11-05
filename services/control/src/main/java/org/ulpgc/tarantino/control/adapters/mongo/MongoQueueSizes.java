package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.control.ports.QueueSizes;

import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;

/**
 * The work waiting in mode {@code polling}, where there are no queues: the {@code QUEUED} and {@code DOWNLOADED} books,
 * which {@code GET /control/status} reports in {@code queues} (SPEC §11.3).
 */
public class MongoQueueSizes implements QueueSizes {

    private final MongoCollection<Document> states;

    public MongoQueueSizes(MongoDatabase database) {
        this.states = database.getCollection(MongoCollections.BOOK_STATES);
    }

    @Override
    public int downloads() {
        return count(BookStatus.QUEUED);
    }

    @Override
    public int index() {
        return count(BookStatus.DOWNLOADED);
    }

    private int count(BookStatus status) {
        return Math.toIntExact(states.countDocuments(Filters.eq(STATUS, status.name())));
    }
}
