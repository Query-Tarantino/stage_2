package org.ulpgc.tarantino.crawler.adapters.mongo;

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
import org.ulpgc.tarantino.crawler.ports.BookClaims;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.ulpgc.tarantino.common.mongo.MongoFields.ATTEMPTS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.CLAIM_ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.DOWNLOADED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ENQUEUED_AT;
import static org.ulpgc.tarantino.common.mongo.MongoFields.ID;
import static org.ulpgc.tarantino.common.mongo.MongoFields.LAST_ERROR;
import static org.ulpgc.tarantino.common.mongo.MongoFields.LEASE_UNTIL;
import static org.ulpgc.tarantino.common.mongo.MongoFields.STATUS;
import static org.ulpgc.tarantino.common.mongo.MongoFields.UPDATED_AT;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.NOW;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.nowPlus;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.setThenUnset;

/** “Claim a book”, “Finish a download” and “Fail a download” (SPEC §5.1), each a single conditional write. */
public class MongoBookClaims implements BookClaims {

    private static final Set<BookErrorCode> RETRYABLE = EnumSet.of(BookErrorCode.NETWORK_ERROR, BookErrorCode.STORAGE_ERROR);

    private final MongoCollection<Document> states;
    private final Duration lease;
    private final int maxAttempts;

    public MongoBookClaims(MongoDatabase database, Duration lease, int maxAttempts) {
        this.states = database.getCollection(MongoCollections.BOOK_STATES);
        this.lease = lease;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public Optional<String> claim(int bookId) {
        String claimId = UUID.randomUUID().toString();
        Document change = new Document(STATUS, BookStatus.DOWNLOADING.name())
                .append(CLAIM_ID, claimId)
                .append(LEASE_UNTIL, nowPlus(lease))
                .append(ATTEMPTS, new Document("$add", List.of("$" + ATTEMPTS, 1)))
                .append(UPDATED_AT, NOW);
        Bson queued = Filters.and(Filters.eq(ID, bookId), Filters.eq(STATUS, BookStatus.QUEUED.name()));
        return states.updateOne(queued, set(change)).getMatchedCount() == 1 ? Optional.of(claimId) : Optional.empty();
    }

    @Override
    public boolean finish(int bookId, String claimId) {
        Document change = new Document(STATUS, BookStatus.DOWNLOADED.name())
                .append(DOWNLOADED_AT, NOW)
                .append(ENQUEUED_AT, NOW)
                .append(UPDATED_AT, NOW)
                .append(LAST_ERROR, null);
        return states.updateOne(downloading(bookId, claimId), setThenUnset(change, CLAIM_ID, LEASE_UNTIL)).getMatchedCount() == 1;
    }

    /**
     * “Fail a download”: back to {@code QUEUED} for a retryable error while attempts are left, otherwise
     * {@code FAILED}; the status the book ended in, or empty if the lease was lost.
     */
    @Override
    public Optional<BookStatus> fail(int bookId, String claimId, BookErrorCode reason) {
        Document outcome = new Document(STATUS, queuedOrFailed(reason))
                .append(LAST_ERROR, reason.name())
                .append(UPDATED_AT, NOW);
        List<Document> change = List.of(new Document("$set", outcome), new Document("$set", enqueuedNowIfQueued()),
                new Document("$unset", List.of(CLAIM_ID, LEASE_UNTIL)));
        FindOneAndUpdateOptions endState = new FindOneAndUpdateOptions()
                .returnDocument(ReturnDocument.AFTER)
                .projection(Projections.include(STATUS));
        return Optional.ofNullable(states.findOneAndUpdate(downloading(bookId, claimId), change, endState))
                .map(state -> BookStatus.valueOf(state.getString(STATUS)));
    }

    private Document queuedOrFailed(BookErrorCode reason) {
        Document attemptsLeft = new Document("$lt", List.of("$" + ATTEMPTS, maxAttempts));
        Document retried = new Document("$and", List.of(RETRYABLE.contains(reason), attemptsLeft));
        return new Document("$cond", List.of(retried, BookStatus.QUEUED.name(), BookStatus.FAILED.name()));
    }

    private static Document enqueuedNowIfQueued() {
        Document queued = new Document("$eq", List.of("$" + STATUS, BookStatus.QUEUED.name()));
        return new Document(ENQUEUED_AT, new Document("$cond", List.of(queued, NOW, "$" + ENQUEUED_AT)));
    }

    private static Bson downloading(int bookId, String claimId) {
        return Filters.and(Filters.eq(ID, bookId), Filters.eq(STATUS, BookStatus.DOWNLOADING.name()), Filters.eq(CLAIM_ID, claimId));
    }
}
