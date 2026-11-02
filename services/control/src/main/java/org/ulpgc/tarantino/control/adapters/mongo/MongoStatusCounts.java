package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import org.bson.Document;
import org.ulpgc.tarantino.common.http.ProblemException;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.ProblemCode;
import org.ulpgc.tarantino.common.mongo.IndexState;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.control.ports.StatusCounts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;

/** The generation and the books per state that {@code GET /control/status} reports (SPEC §6.5). */
public class MongoStatusCounts implements StatusCounts {

    private static final String COUNT = "count";
    private static final String POINTER_NOT_CREATED = "control is still starting: the generation pointer does not exist yet";

    private final IndexState indexState;
    private final MongoCollection<Document> states;

    public MongoStatusCounts(MongoDatabase database) {
        this.indexState = new IndexState(database);
        this.states = database.getCollection(MongoCollections.BOOK_STATES);
    }

    /** {@code 503} until the startup creates the pointer, because the port opens first (SPEC §2.4). */
    @Override
    public int currentGeneration() {
        try {
            return indexState.currentGeneration();
        } catch (IllegalStateException pointerNotCreatedYet) {
            throw new ProblemException(ProblemCode.DEPENDENCY_UNAVAILABLE, POINTER_NOT_CREATED);
        }
    }

    @Override
    public Map<BookStatus, Long> booksByStatus() {
        return states.aggregate(List.of(Aggregates.group("$" + STATUS, Accumulators.sum(COUNT, 1))))
                .into(new ArrayList<>()).stream()
                .collect(Collectors.toMap(group -> BookStatus.valueOf(group.getString(ID)),
                        group -> group.get(COUNT, Number.class).longValue()));
    }
}
