package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoCollection;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.nowPlus;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;

class MongoLostMessagesTest {

    private static final Duration REQUEUE = Duration.ofSeconds(60);
    private static final Duration LONG_AGO = Duration.ofHours(-1);
    private static final Duration RECENTLY = Duration.ofSeconds(-1);

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void onlyOneOfTwoRequeuesAtOnceMatches() throws Exception {
        waiting(1342, BookStatus.QUEUED, LONG_AGO);
        Document before = state(1342);

        List<Boolean> outcomes = TwoAtOnce.call(() -> messages().requeue(1342, BookStatus.QUEUED));

        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        Document requeued = state(1342);
        assertEquals(BookStatus.QUEUED.name(), requeued.getString(MongoFields.STATUS));
        assertTrue(requeued.getDate(MongoFields.ENQUEUED_AT).after(before.getDate(MongoFields.ENQUEUED_AT)));
    }

    @Test
    void doesNotRequeueABookEnqueuedRecently() {
        waiting(1342, BookStatus.DOWNLOADED, RECENTLY);

        assertFalse(messages().requeue(1342, BookStatus.DOWNLOADED));
    }

    @Test
    void doesNotRequeueABookThatMovedToAnotherState() {
        waiting(1342, BookStatus.DOWNLOADING, LONG_AGO);

        assertFalse(messages().requeue(1342, BookStatus.QUEUED));
        assertEquals(BookStatus.DOWNLOADING.name(), state(1342).getString(MongoFields.STATUS));
    }

    @Test
    void findsOnlyTheBooksThatWaitedTooLongInThatState() {
        waiting(1342, BookStatus.QUEUED, LONG_AGO);
        waiting(84, BookStatus.QUEUED, RECENTLY);
        waiting(11, BookStatus.DOWNLOADED, LONG_AGO);

        assertEquals(List.of(1342), messages().lost(BookStatus.QUEUED));
        assertEquals(List.of(11), messages().lost(BookStatus.DOWNLOADED));
    }

    private void waiting(int bookId, BookStatus status, Duration enqueuedAgo) {
        new BookStates(database()).create(bookId);
        states().updateOne(Filters.eq(MongoFields.ID, bookId), set(new Document(MongoFields.STATUS, status.name())
                .append(MongoFields.ENQUEUED_AT, nowPlus(enqueuedAgo))));
    }

    private MongoLostMessages messages() {
        return new MongoLostMessages(database(), REQUEUE);
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
