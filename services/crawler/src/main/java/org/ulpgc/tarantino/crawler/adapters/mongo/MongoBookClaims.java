package org.ulpgc.tarantino.crawler.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.crawler.ports.BookClaims;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
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

/** “Claim a book” and “Finish a download” (SPEC §5.1), each a single conditional write. */
public class MongoBookClaims implements BookClaims {

    private final MongoCollection<Document> states;
    private final Duration lease;

    public MongoBookClaims(MongoDatabase database, Duration lease) {
        this.states = database.getCollection(MongoCollections.BOOK_STATES);
        this.lease = lease;
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

    private static Bson downloading(int bookId, String claimId) {
        return Filters.and(Filters.eq(ID, bookId), Filters.eq(STATUS, BookStatus.DOWNLOADING.name()), Filters.eq(CLAIM_ID, claimId));
    }
}
