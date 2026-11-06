package org.ulpgc.tarantino.control.adapters.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.mongo.BookStates;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoFields;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;

class MongoWaitingBooksTest {

    private static final Instant FIRST = Instant.parse("2026-11-06T10:00:00Z");
    private static final Instant SECOND = FIRST.plusSeconds(1);
    private static final Instant THIRD = FIRST.plusSeconds(2);

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void readsTheStateOfEachBookThatHasADocument() {
        waiting(1342, BookStatus.QUEUED, FIRST);
        waiting(84, BookStatus.DOWNLOADED, FIRST);

        Map<Integer, BookStatus> statuses = books().statuses(List.of(1342, 84, 11));

        assertEquals(Map.of(1342, BookStatus.QUEUED, 84, BookStatus.DOWNLOADED), statuses);
    }

    @Test
    void returnsTheOldestBooksInTheStatusOutsideTheExcludedUpToTheLimit() {
        waiting(1342, BookStatus.QUEUED, THIRD);
        waiting(11, BookStatus.QUEUED, THIRD);
        waiting(2701, BookStatus.QUEUED, FIRST);
        waiting(84, BookStatus.QUEUED, SECOND);
        waiting(98, BookStatus.DOWNLOADED, FIRST);

        List<BookTask> oldest = books().oldest(BookStatus.QUEUED, List.of(2701), 2);

        assertEquals(List.of(new BookTask(84, SECOND), new BookTask(11, THIRD)), oldest);
    }

    @Test
    void returnsNothingWithoutRoom() {
        waiting(1342, BookStatus.QUEUED, FIRST);

        assertEquals(List.of(), books().oldest(BookStatus.QUEUED, List.of(), 0));
    }

    private void waiting(int bookId, BookStatus status, Instant enqueuedAt) {
        new BookStates(database()).create(bookId);
        database().getCollection(MongoCollections.BOOK_STATES).updateOne(Filters.eq(MongoFields.ID, bookId),
                set(new Document(MongoFields.STATUS, status.name()).append(MongoFields.ENQUEUED_AT, Date.from(enqueuedAt))));
    }

    private MongoWaitingBooks books() {
        return new MongoWaitingBooks(database());
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }
}
