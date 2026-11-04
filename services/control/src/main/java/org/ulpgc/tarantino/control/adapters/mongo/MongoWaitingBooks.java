package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.control.ports.WaitingBooks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.ulpgc.tarantino.common.mongo.MongoFields.ENQUEUED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;

/** The orchestrator's reads of {@code book_states} (SPEC §11.4), on the index {@code {status: 1, enqueued_at: 1}}. */
public class MongoWaitingBooks implements WaitingBooks {

    private final MongoCollection<Document> states;

    public MongoWaitingBooks(MongoDatabase database) {
        this.states = database.getCollection(MongoCollections.BOOK_STATES);
    }

    @Override
    public Map<Integer, BookStatus> statuses(Collection<Integer> bookIds) {
        return states.find(Filters.in(ID, bookIds)).projection(Projections.include(STATUS)).into(new ArrayList<>()).stream()
                .collect(Collectors.toMap(state -> state.getInteger(ID), state -> BookStatus.valueOf(state.getString(STATUS))));
    }

    @Override
    public List<BookTask> oldest(BookStatus status, Collection<Integer> excluded, int limit) {
        if (limit < 1) {
            return List.of();
        }
        return states.find(Filters.and(Filters.eq(STATUS, status.name()), Filters.nin(ID, excluded)))
                .projection(Projections.include(ID, ENQUEUED_AT))
                .sort(Sorts.ascending(ENQUEUED_AT, ID))
                .limit(limit)
                .map(state -> new BookTask(state.getInteger(ID), state.getDate(ENQUEUED_AT).toInstant()))
                .into(new ArrayList<>());
    }
}
