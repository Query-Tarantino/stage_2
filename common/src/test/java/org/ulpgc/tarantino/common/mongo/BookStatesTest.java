package org.ulpgc.tarantino.common.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.model.BookErrorCode;
import org.ulpgc.tarantino.common.model.BookState;
import org.ulpgc.tarantino.common.model.BookStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class BookStatesTest {

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void onlyOneOfTwoCallsAtOnceCreatesTheState() throws Exception {
        BookStates states = states();

        List<Boolean> created = TwoAtOnce.call(() -> states.create(1342));

        assertEquals(1, created.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, collection().countDocuments());
    }

    @Test
    void createsAQueuedBookWithoutAttempts() {
        BookStates states = states();

        states.create(1342);

        BookState state = states.state(1342).orElseThrow();
        assertEquals(BookStatus.QUEUED, state.status());
        assertEquals(0, state.attempts());
        assertNull(state.lastError());
    }

    @Test
    void neverChangesAnExistingState() {
        BookStates states = states();
        states.create(1342);
        collection().updateOne(Filters.eq(MongoFields.ID, 1342), Updates.combine(
                Updates.set(MongoFields.STATUS, BookStatus.FAILED.name()),
                Updates.set(MongoFields.ATTEMPTS, 3),
                Updates.set(MongoFields.LAST_ERROR, BookErrorCode.NOT_FOUND.name())));

        assertFalse(states.create(1342));

        BookState state = states.state(1342).orElseThrow();
        assertEquals(BookStatus.FAILED, state.status());
        assertEquals(3, state.attempts());
        assertEquals(BookErrorCode.NOT_FOUND, state.lastError());
    }

    @Test
    void hasNoStateForABookWithoutDocument() {
        assertEquals(Optional.empty(), states().state(84));
    }

    private BookStates states() {
        return new BookStates(MongoDatabases.database(mongo.uri()));
    }

    private MongoCollection<Document> collection() {
        return MongoDatabases.database(mongo.uri()).getCollection(MongoCollections.BOOK_STATES);
    }
}
