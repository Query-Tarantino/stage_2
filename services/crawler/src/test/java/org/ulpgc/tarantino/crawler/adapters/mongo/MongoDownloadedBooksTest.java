package org.ulpgc.tarantino.crawler.adapters.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.mongo.BookStates;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoFields;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MongoDownloadedBooksTest {

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void listsTheDownloadedIndexingAndPublishedBooksInAscendingOrder() {
        Map.of(1342, BookStatus.PUBLISHED, 17, BookStatus.INDEXING, 5, BookStatus.DOWNLOADED,
                        84, BookStatus.QUEUED, 11, BookStatus.DOWNLOADING, 98, BookStatus.FAILED)
                .forEach(this::book);

        assertEquals(List.of(5, 17, 1342), new MongoDownloadedBooks(database()).downloaded());
    }

    private void book(int bookId, BookStatus status) {
        new BookStates(database()).create(bookId);
        database().getCollection(MongoCollections.BOOK_STATES)
                .updateOne(Filters.eq(MongoFields.ID, bookId), Updates.set(MongoFields.STATUS, status.name()));
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }
}
