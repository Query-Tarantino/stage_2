package org.ulpgc.tarantino.crawler.adapters.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.model.BookErrorCode;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoFields;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;
import org.ulpgc.tarantino.common.mongo.TwoAtOnce;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoIngestRequestsTest {

    private static final Duration LEASE = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 3;
    private static final Instant OLD_ENQUEUED_AT = Instant.parse("2026-10-21T10:00:00Z");

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void onlyOneOfTwoRetriesAtOnceRequeuesAFailedBook() throws Exception {
        MongoIngestRequests requests = new MongoIngestRequests(database());
        failedWithAnOldEnqueuedAt(requests);

        List<Boolean> outcomes = TwoAtOnce.call(() -> requests.retry(1342));

        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        Document state = state();
        assertEquals(BookStatus.QUEUED.name(), state.getString(MongoFields.STATUS));
        assertEquals(0, state.getInteger(MongoFields.ATTEMPTS));
        assertTrue(state.containsKey(MongoFields.LAST_ERROR));
        assertNull(state.get(MongoFields.LAST_ERROR));
        assertTrue(state.getDate(MongoFields.ENQUEUED_AT).toInstant().isAfter(OLD_ENQUEUED_AT));
    }

    @Test
    void doesNotRetryABookThatIsNotFailed() {
        MongoIngestRequests requests = new MongoIngestRequests(database());
        requests.create(1342);

        assertFalse(requests.retry(1342));
        assertEquals(BookStatus.QUEUED.name(), state().getString(MongoFields.STATUS));
    }

    private void failedWithAnOldEnqueuedAt(MongoIngestRequests requests) {
        requests.create(1342);
        MongoBookClaims claims = new MongoBookClaims(database(), LEASE, MAX_ATTEMPTS);
        claims.fail(1342, claims.claim(1342).orElseThrow(), BookErrorCode.NOT_FOUND);
        database().getCollection(MongoCollections.BOOK_STATES)
                .updateOne(Filters.eq(MongoFields.ID, 1342), Updates.set(MongoFields.ENQUEUED_AT, Date.from(OLD_ENQUEUED_AT)));
    }

    private Document state() {
        return database().getCollection(MongoCollections.BOOK_STATES).find(Filters.eq(MongoFields.ID, 1342)).first();
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }
}
