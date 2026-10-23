package org.ulpgc.tarantino.indexer.adapters.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.model.BookErrorCode;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoFields;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;
import org.ulpgc.tarantino.common.mongo.TwoAtOnce;
import org.ulpgc.tarantino.indexer.model.batch.Batch;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoBatchStoreTest {

    private static final Duration LEASE = Duration.ofSeconds(60);
    private static final List<Integer> BOOKS = IntStream.rangeClosed(1, 20).boxed().toList();

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @Test
    void opensAWritingBatchWithALeaseAndTheIndexesOfItsGeneration() {
        Batch batch = store(LEASE).open(1);

        Document document = batches().find(Filters.eq(MongoFields.ID, new ObjectId(batch.batchId()))).first();
        assertEquals(BatchStatus.WRITING.name(), document.getString(MongoFields.STATUS));
        assertEquals(batch.claimId(), document.getString(MongoFields.CLAIM_ID));
        assertEquals("indexer-test", document.getString(MongoFields.OWNER));
        assertEquals(List.of(), document.getList(MongoFields.BOOK_IDS, Integer.class));
        assertTrue(document.getDate(MongoFields.LEASE_UNTIL).after(new Date()));
        assertTrue(database().getCollection(MongoCollections.postings(1)).listIndexes().into(new ArrayList<>()).size() > 1);
    }

    @Test
    void twoIndexersClaimingTheSameBooksAtOnceNeverShareOne() throws Exception {
        MongoBatchStore store = store(LEASE);
        BOOKS.forEach(this::downloaded);

        List<List<Integer>> claimed = TwoAtOnce.call(() -> store.claim(store.open(1), BOOKS));

        Set<Integer> union = new HashSet<>(claimed.get(0));
        union.addAll(claimed.get(1));
        assertEquals(Set.copyOf(BOOKS), union);
        assertEquals(BOOKS.size(), claimed.get(0).size() + claimed.get(1).size());
    }

    @Test
    void failsAtIndexingOnlyABookThatIsStillInTheBatch() {
        MongoBatchStore store = store(LEASE);
        downloaded(1342);
        Batch batch = store.open(1);
        store.claim(batch, List.of(1342));

        assertTrue(store.failAtIndexing(batch, 1342));
        assertFalse(store.failAtIndexing(batch, 1342));

        Document state = state(1342);
        assertEquals(BookStatus.FAILED.name(), state.getString(MongoFields.STATUS));
        assertEquals(BookErrorCode.NOT_IN_DATALAKE.name(), state.getString(MongoFields.LAST_ERROR));
        assertFalse(state.containsKey(MongoFields.BATCH_ID));
        assertEquals(1, state.getInteger(MongoFields.GENERATION));
    }

    @Test
    void onlyOneOfTwoPublicationsAtOnceMatchesAndBothMarkTheSameBooks() throws Exception {
        MongoBatchStore store = store(LEASE);
        downloaded(1342);
        downloaded(84);
        Batch batch = store.open(1);
        List<Integer> claimed = store.claim(batch, List.of(1342, 84));

        List<Optional<Instant>> published = TwoAtOnce.call(() -> store.publish(batch, claimed));
        store.markPublished(batch);

        assertEquals(1, published.stream().filter(Optional::isPresent).count());
        Document document = batches().find(Filters.eq(MongoFields.ID, new ObjectId(batch.batchId()))).first();
        assertEquals(BatchStatus.PUBLISHED.name(), document.getString(MongoFields.STATUS));
        assertEquals(List.of(84, 1342), document.getList(MongoFields.BOOK_IDS, Integer.class));
        assertFalse(document.containsKey(MongoFields.LEASE_UNTIL));
        assertEquals(BookStatus.PUBLISHED.name(), state(1342).getString(MongoFields.STATUS));
        assertEquals(BookStatus.PUBLISHED.name(), state(84).getString(MongoFields.STATUS));
    }

    @Test
    void neverPublishesABatchWhoseLeaseExpired() throws InterruptedException {
        MongoBatchStore store = store(Duration.ofMillis(1));
        downloaded(1342);
        Batch batch = store.open(1);
        store.claim(batch, List.of(1342));
        Thread.sleep(50);

        assertEquals(Optional.empty(), store.publish(batch, List.of(1342)));
    }

    private MongoBatchStore store(Duration lease) {
        return new MongoBatchStore(database(), lease, "indexer-test");
    }

    private void downloaded(int bookId) {
        states().insertOne(new Document(MongoFields.ID, bookId).append(MongoFields.STATUS, BookStatus.DOWNLOADED.name())
                .append(MongoFields.ATTEMPTS, 1).append(MongoFields.UPDATED_AT, new Date()));
    }

    private Document state(int bookId) {
        return states().find(Filters.eq(MongoFields.ID, bookId)).first();
    }

    private MongoCollection<Document> states() {
        return database().getCollection(MongoCollections.BOOK_STATES);
    }

    private MongoCollection<Document> batches() {
        return database().getCollection(MongoCollections.batches(1));
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }
}
