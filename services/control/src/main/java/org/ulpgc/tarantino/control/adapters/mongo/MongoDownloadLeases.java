package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.ulpgc.tarantino.common.model.BookErrorCode;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.control.model.DownloadLease;
import org.ulpgc.tarantino.control.ports.DownloadLeases;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.ulpgc.tarantino.common.mongo.MongoFields.ATTEMPTS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.CLAIM_ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ENQUEUED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.LAST_ERROR;
import static org.ulpgc.tarantino.common.mongo.MongoFields.LEASE_UNTIL;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.UPDATED_AT;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.NOW;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.setThenUnset;

/** “Release an expired download” (SPEC §5.1) over the expired downloads of step 1 of SPEC §5.3. */
public class MongoDownloadLeases implements DownloadLeases {

    private final MongoCollection<Document> states;
    private final int maxAttempts;

    public MongoDownloadLeases(MongoDatabase database, int maxAttempts) {
        this.states = database.getCollection(MongoCollections.BOOK_STATES);
        this.maxAttempts = maxAttempts;
    }

    @Override
    public List<DownloadLease> expired() {
        return states.find(Filters.and(downloading(), ServerTime.passed(LEASE_UNTIL)))
                .projection(Projections.include(CLAIM_ID))
                .map(state -> new DownloadLease(state.getInteger(ID), state.getString(CLAIM_ID)))
                .into(new ArrayList<>());
    }

    @Override
    public Optional<BookStatus> release(DownloadLease lease) {
        Bson expiredClaim = Filters.and(Filters.eq(ID, lease.bookId()), downloading(), Filters.eq(CLAIM_ID, lease.claimId()),
                ServerTime.passed(LEASE_UNTIL));
        FindOneAndUpdateOptions after = new FindOneAndUpdateOptions()
                .returnDocument(ReturnDocument.AFTER)
                .projection(Projections.include(STATUS));
        return Optional.ofNullable(states.findOneAndUpdate(expiredClaim, releasePipeline(), after))
                .map(state -> BookStatus.valueOf(state.getString(STATUS)));
    }

    private List<Document> releasePipeline() {
        Document outcome = new Document(STATUS, queuedWhileAttemptsLeft())
                .append(LAST_ERROR, BookErrorCode.LEASE_EXPIRED.name())
                .append(UPDATED_AT, NOW);
        Document enqueuedIfQueued = new Document(ENQUEUED_AT, new Document("$cond", List.of(
                new Document("$eq", List.of("$" + STATUS, BookStatus.QUEUED.name())), NOW, "$" + ENQUEUED_AT)));
        return Stream.concat(set(outcome).stream(), setThenUnset(enqueuedIfQueued, CLAIM_ID, LEASE_UNTIL).stream()).toList();
    }

    private Document queuedWhileAttemptsLeft() {
        return new Document("$cond", List.of(new Document("$lt", List.of("$" + ATTEMPTS, maxAttempts)),
                BookStatus.QUEUED.name(), BookStatus.FAILED.name()));
    }

    private static Bson downloading() {
        return Filters.eq(STATUS, BookStatus.DOWNLOADING.name());
    }
}
