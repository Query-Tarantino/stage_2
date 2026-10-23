package org.ulpgc.tarantino.crawler.adapters.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.mongo.BookStates;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoFields;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;
import org.ulpgc.tarantino.common.mongo.TwoAtOnce;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoBookClaimsTest {

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

    private MongoBookClaims claims() {
        return new MongoBookClaims(database(), Duration.ofSeconds(60));
    }

    private Document state() {
        return database().getCollection(MongoCollections.BOOK_STATES).find(Filters.eq(MongoFields.ID, 1342)).first();
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }
}
