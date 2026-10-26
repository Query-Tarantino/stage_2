package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.model.BookErrorCode;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.mongo.BookStates;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoFields;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;
import org.ulpgc.tarantino.common.mongo.TwoAtOnce;
import org.ulpgc.tarantino.control.model.DownloadLease;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.nowPlus;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;

class MongoDownloadLeasesTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration EXPIRED = Duration.ofSeconds(-1);
    private static final Duration ALIVE = Duration.ofSeconds(60);
    private static final Duration LONG_AGO = Duration.ofHours(-1);

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void onlyOneOfTwoReleasesAtOnceMatches() throws Exception {
        DownloadLease lease = downloading(1342, 1, EXPIRED);

        List<Optional<BookStatus>> outcomes = TwoAtOnce.call(() -> leases().release(lease));

        assertEquals(1, outcomes.stream().filter(Optional::isPresent).count());
        Document state = state(1342);
        assertEquals(BookStatus.QUEUED.name(), state.getString(MongoFields.STATUS));
        assertEquals(BookErrorCode.LEASE_EXPIRED.name(), state.getString(MongoFields.LAST_ERROR));
        assertFalse(state.containsKey(MongoFields.CLAIM_ID));
        assertFalse(state.containsKey(MongoFields.LEASE_UNTIL));
    }

    @Test
    void queuesTheBookAgainFromNowWhileItHasAttemptsLeft() {
        DownloadLease lease = downloading(1342, MAX_ATTEMPTS - 1, EXPIRED);
        Document before = state(1342);

        assertEquals(Optional.of(BookStatus.QUEUED), leases().release(lease));

        assertTrue(state(1342).getDate(MongoFields.ENQUEUED_AT).after(before.getDate(MongoFields.ENQUEUED_AT)));
    }

    @Test
    void failsTheBookOnceItsAttemptsAreUsedAndKeepsItsEnqueuedAt() {
        DownloadLease lease = downloading(1342, MAX_ATTEMPTS, EXPIRED);
        Document before = state(1342);

        assertEquals(Optional.of(BookStatus.FAILED), leases().release(lease));

        Document state = state(1342);
        assertEquals(BookErrorCode.LEASE_EXPIRED.name(), state.getString(MongoFields.LAST_ERROR));
        assertEquals(before.getDate(MongoFields.ENQUEUED_AT), state.getDate(MongoFields.ENQUEUED_AT));
    }

    @Test
    void doesNotReleaseALeaseThatIsStillAlive() {
        DownloadLease lease = downloading(1342, 1, ALIVE);

        assertEquals(Optional.empty(), leases().release(lease));
        assertEquals(BookStatus.DOWNLOADING.name(), state(1342).getString(MongoFields.STATUS));
    }

    @Test
    void doesNotReleaseTheClaimOfAnotherWorker() {
        downloading(1342, 1, EXPIRED);

        assertEquals(Optional.empty(), leases().release(new DownloadLease(1342, "another-claim")));
        assertEquals(BookStatus.DOWNLOADING.name(), state(1342).getString(MongoFields.STATUS));
    }

    @Test
    void findsOnlyTheDownloadsWhoseLeaseHasPassed() {
        DownloadLease expired = downloading(1342, 1, EXPIRED);
        downloading(84, 1, ALIVE);
        new BookStates(database()).create(11);

        assertEquals(List.of(expired), leases().expired());
    }

    private DownloadLease downloading(int bookId, int attempts, Duration leaseLeft) {
        new BookStates(database()).create(bookId);
        String claimId = "claim-" + bookId;
        states().updateOne(Filters.eq(MongoFields.ID, bookId), set(new Document(MongoFields.STATUS, BookStatus.DOWNLOADING.name())
                .append(MongoFields.CLAIM_ID, claimId)
                .append(MongoFields.ATTEMPTS, attempts)
                .append(MongoFields.LEASE_UNTIL, nowPlus(leaseLeft))
                .append(MongoFields.ENQUEUED_AT, nowPlus(LONG_AGO))));
        return new DownloadLease(bookId, claimId);
    }

    private MongoDownloadLeases leases() {
        return new MongoDownloadLeases(database(), MAX_ATTEMPTS);
    }

    private Document state(int bookId) {
        return states().find(Filters.eq(MongoFields.ID, bookId)).first();
    }

    private MongoCollection<Document> states() {
        return database().getCollection(MongoCollections.BOOK_STATES);
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }
}
