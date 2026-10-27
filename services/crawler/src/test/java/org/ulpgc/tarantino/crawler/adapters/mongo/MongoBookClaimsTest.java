package org.ulpgc.tarantino.crawler.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoBookClaimsTest {

    private static final Duration LEASE = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 3;
    private static final Instant OLD_ENQUEUED_AT = Instant.parse("2026-10-21T10:00:00Z");

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void onlyOneOfTwoWorkersAtOnceClaimsABook() throws Exception {
        MongoBookClaims claims = claims();
        new BookStates(database()).create(1342);

        List<Optional<String>> outcomes = TwoAtOnce.call(() -> claims.claim(1342));

        assertEquals(1, outcomes.stream().filter(Optional::isPresent).count());
        Document state = state();
        assertEquals(BookStatus.DOWNLOADING.name(), state.getString(MongoFields.STATUS));
        assertEquals(1, state.getInteger(MongoFields.ATTEMPTS));
        assertNotNull(state.getDate(MongoFields.LEASE_UNTIL));
    }

    @Test
    void onlyOneOfTwoFinishesAtOnceMatchesAndTheLeaseGoes() throws Exception {
        MongoBookClaims claims = claims();
        new BookStates(database()).create(1342);
        String claimId = claims.claim(1342).orElseThrow();

        List<Boolean> outcomes = TwoAtOnce.call(() -> claims.finish(1342, claimId));

        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        Document state = state();
        assertEquals(BookStatus.DOWNLOADED.name(), state.getString(MongoFields.STATUS));
        assertNotNull(state.getDate(MongoFields.DOWNLOADED_AT));
        assertTrue(state.containsKey(MongoFields.LAST_ERROR));
        assertNull(state.get(MongoFields.LAST_ERROR));
        assertFalse(state.containsKey(MongoFields.CLAIM_ID));
        assertFalse(state.containsKey(MongoFields.LEASE_UNTIL));
    }

    @Test
    void doesNotFinishWithAnotherClaim() {
        MongoBookClaims claims = claims();
        new BookStates(database()).create(1342);
        claims.claim(1342);

        assertFalse(claims.finish(1342, "another-claim"));
        assertEquals(BookStatus.DOWNLOADING.name(), state().getString(MongoFields.STATUS));
    }

    @Test
    void onlyOneOfTwoFailuresAtOnceMatchesAndRequeuesARetryableError() throws Exception {
        MongoBookClaims claims = claims();
        String claimId = claimedWithAnOldEnqueuedAt(claims);

        List<Optional<BookStatus>> outcomes = TwoAtOnce.call(() -> claims.fail(1342, claimId, BookErrorCode.NETWORK_ERROR));

        assertEquals(List.of(BookStatus.QUEUED), outcomes.stream().flatMap(Optional::stream).toList());
        Document state = state();
        assertEquals(BookStatus.QUEUED.name(), state.getString(MongoFields.STATUS));
        assertEquals(BookErrorCode.NETWORK_ERROR.name(), state.getString(MongoFields.LAST_ERROR));
        assertTrue(state.getDate(MongoFields.ENQUEUED_AT).toInstant().isAfter(OLD_ENQUEUED_AT));
        assertFalse(state.containsKey(MongoFields.CLAIM_ID));
        assertFalse(state.containsKey(MongoFields.LEASE_UNTIL));
    }

    @Test
    void failsWithoutRetriesWhenTheBookIsNotFound() {
        MongoBookClaims claims = claims();
        String claimId = claimedWithAnOldEnqueuedAt(claims);

        assertEquals(Optional.of(BookStatus.FAILED), claims.fail(1342, claimId, BookErrorCode.NOT_FOUND));
        assertEquals(OLD_ENQUEUED_AT, state().getDate(MongoFields.ENQUEUED_AT).toInstant());
        assertEquals(BookErrorCode.NOT_FOUND.name(), state().getString(MongoFields.LAST_ERROR));
    }

    @Test
    void failsARetryableErrorOnceTheAttemptsAreUsed() {
        MongoBookClaims claims = new MongoBookClaims(database(), LEASE, 1);
        String claimId = claimedWithAnOldEnqueuedAt(claims);

        assertEquals(Optional.of(BookStatus.FAILED), claims.fail(1342, claimId, BookErrorCode.STORAGE_ERROR));
        assertEquals(OLD_ENQUEUED_AT, state().getDate(MongoFields.ENQUEUED_AT).toInstant());
    }

    @Test
    void doesNotFailWithAnotherClaim() {
        MongoBookClaims claims = claims();
        claimedWithAnOldEnqueuedAt(claims);

        assertEquals(Optional.empty(), claims.fail(1342, "another-claim", BookErrorCode.NETWORK_ERROR));
        assertEquals(BookStatus.DOWNLOADING.name(), state().getString(MongoFields.STATUS));
    }

    @Test
    void onlyOneOfTwoRetriesAtOnceRequeuesAFailedBook() throws Exception {
        MongoBookClaims claims = claims();
        claims.fail(1342, claimedWithAnOldEnqueuedAt(claims), BookErrorCode.NOT_FOUND);

        List<Boolean> outcomes = TwoAtOnce.call(() -> claims.retry(1342));

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
        new BookStates(database()).create(1342);

        assertFalse(claims().retry(1342));
        assertEquals(BookStatus.QUEUED.name(), state().getString(MongoFields.STATUS));
    }

    private String claimedWithAnOldEnqueuedAt(MongoBookClaims claims) {
        new BookStates(database()).create(1342);
        collection().updateOne(Filters.eq(MongoFields.ID, 1342), Updates.set(MongoFields.ENQUEUED_AT, Date.from(OLD_ENQUEUED_AT)));
        return claims.claim(1342).orElseThrow();
    }

    private MongoBookClaims claims() {
        return new MongoBookClaims(database(), LEASE, MAX_ATTEMPTS);
    }

    private Document state() {
        return collection().find(Filters.eq(MongoFields.ID, 1342)).first();
    }

    private MongoCollection<Document> collection() {
        return database().getCollection(MongoCollections.BOOK_STATES);
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }
}
