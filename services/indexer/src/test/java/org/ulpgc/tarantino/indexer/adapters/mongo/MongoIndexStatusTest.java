package org.ulpgc.tarantino.indexer.adapters.mongo;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.RebuildStatus;
import org.ulpgc.tarantino.common.mongo.IndexState;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoFields;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;
import org.ulpgc.tarantino.indexer.model.rebuild.IndexStatus;
import org.ulpgc.tarantino.indexer.model.rebuild.RebuildProgress;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MongoIndexStatusTest {

    private static final Instant FIRST_PUBLICATION = Instant.parse("2026-10-21T10:06:00Z");
    private static final Instant LAST_PUBLICATION = Instant.parse("2026-10-21T10:07:00Z");

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @BeforeEach
    void createThePointer() {
        new IndexState(database()).createPointer();
    }

    @Test
    void startsEmptyBeforeTheFirstBatch() {
        assertEquals(new IndexStatus(1, 0, 0, null, 0.0, null), new MongoIndexStatus(database()).status());
    }

    @Test
    void countsOnlyThePublishedBatchesOfTheCurrentGeneration() {
        batch(1, BatchStatus.PUBLISHED, List.of(84, 1342), FIRST_PUBLICATION);
        batch(1, BatchStatus.PUBLISHED, List.of(2600), LAST_PUBLICATION);
        batch(1, BatchStatus.WRITING, List.of(), null);
        batch(1, BatchStatus.ABANDONED, List.of(), null);
        batch(2, BatchStatus.PUBLISHED, List.of(11), LAST_PUBLICATION);

        IndexStatus status = new MongoIndexStatus(database()).status();

        assertEquals(1, status.generation());
        assertEquals(3, status.booksIndexed());
        assertEquals(2, status.batchesPublished());
        assertEquals(LAST_PUBLICATION, status.lastUpdate());
        assertNull(status.rebuild());
    }

    @Test
    void reportsTheBooksThatBlockARunningRebuild() {
        database().getCollection(MongoCollections.INDEX_STATE).insertOne(new Document(MongoFields.ID, MongoCollections.REBUILD)
                .append(MongoFields.TARGET_GENERATION, 2).append(MongoFields.STATUS, RebuildStatus.RUNNING.name()));
        book(84, BookStatus.PUBLISHED, 1);
        book(1342, BookStatus.FAILED, 1);
        book(2600, BookStatus.DOWNLOADED, 2);

        assertEquals(new RebuildProgress(2, RebuildStatus.RUNNING, 2), new MongoIndexStatus(database()).status().rebuild());
    }

    @Test
    void givesTheSizeInMebibytesWithOneDecimal() {
        assertEquals(0.0, MongoIndexStatus.mebibytes(0));
        assertEquals(512.4, MongoIndexStatus.mebibytes(537_291_571));
    }

    private void batch(int generation, BatchStatus status, List<Integer> bookIds, Instant publishedAt) {
        Document batch = new Document(MongoFields.ID, new ObjectId()).append(MongoFields.STATUS, status.name())
                .append(MongoFields.BOOK_IDS, bookIds);
        if (publishedAt != null) {
            batch.append(MongoFields.PUBLISHED_AT, Date.from(publishedAt));
        }
        database().getCollection(MongoCollections.batches(generation)).insertOne(batch);
    }

    private void book(int bookId, BookStatus status, int generation) {
        database().getCollection(MongoCollections.BOOK_STATES).insertOne(new Document(MongoFields.ID, bookId)
                .append(MongoFields.STATUS, status.name()).append(MongoFields.GENERATION, generation));
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }
}
