package org.ulpgc.tarantino.control;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.hazelcast.HazelcastContracts;
import org.ulpgc.tarantino.common.json.TarantinoJson;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.model.BookErrorCode;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.model.RebuildStatus;
import org.ulpgc.tarantino.common.mongo.BookStates;
import org.ulpgc.tarantino.common.mongo.IndexState;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoFields;
import org.ulpgc.tarantino.common.mongo.MongoIndexes;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;
import org.ulpgc.tarantino.common.startup.Connections;
import org.ulpgc.tarantino.control.commands.ReconcileCommand;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.NOW;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.nowPlus;
import static org.ulpgc.tarantino.common.mongo.UpdatePipelines.set;

/** One reconciliation of SPEC §5.3 as {@code Main} builds it, on MongoDB and an embedded Hazelcast member. */
class ReconcileWholePassTest {

    private static final String CLUSTER = "test-" + UUID.randomUUID();
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration EXPIRED = Duration.ofSeconds(-1);
    private static final Duration LONG_AGO = Duration.ofHours(-1);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    private HazelcastInstance member;
    private Connections connections;

    @AfterEach
    void shutDown() {
        if (connections != null) {
            connections.hazelcast().client().shutdown();
        }
        if (member != null) {
            member.shutdown();
        }
    }

    @Test
    void repairsWhatCrashesLeftHalfDoneInOnePass() throws Exception {
        ReconcileCommand reconcile = reconcileCommand();
        downloading(1342, MAX_ATTEMPTS);
        String expiredBatch = batch(1, BatchStatus.WRITING, MongoFields.LEASE_UNTIL, EXPIRED);
        indexing(84, expiredBatch);
        String publishedBatch = batch(1, BatchStatus.PUBLISHED, MongoFields.PUBLISHED_AT, LONG_AGO);
        indexing(11, publishedBatch);
        queuedLongAgo(2701);
        String abandonedBatch = batch(1, BatchStatus.ABANDONED, MongoFields.ABANDONED_AT, LONG_AGO);
        posting(abandonedBatch);

        reconcile.execute();

        assertEquals(BookStatus.FAILED.name(), status(1342));
        assertEquals(BookErrorCode.LEASE_EXPIRED.name(), state(1342).getString(MongoFields.LAST_ERROR));
        assertEquals(BatchStatus.ABANDONED.name(), batchDocument(1, expiredBatch).getString(MongoFields.STATUS));
        assertEquals(BookStatus.DOWNLOADED.name(), status(84));
        assertEquals(BookStatus.PUBLISHED.name(), status(11));
        assertEquals(0, collection(MongoCollections.postings(1)).countDocuments());
        assertEquals(List.of(2701), enqueued(HazelcastContracts.BOOKS_TO_DOWNLOAD));
        assertEquals(List.of(84), enqueued(HazelcastContracts.BOOKS_TO_INDEX));
        assertTrue(reconcile.lastReconciliation().isPresent());
    }

    @Test
    void switchesToTheRebuiltGenerationAndDropsTheOldOneAfterTheGracePeriod() throws Exception {
        ReconcileCommand reconcile = reconcileCommand();
        runRebuildTo(2);
        bookOfGeneration(1342, BookStatus.PUBLISHED, 2);
        bookOfGeneration(84, BookStatus.FAILED, 1);
        List.of(MongoCollections.metadata(1), MongoCollections.postings(1), MongoCollections.batches(1), MongoCollections.batches(2))
                .forEach(database()::createCollection);

        reconcile.execute();

        assertEquals(2, new IndexState(database()).currentGeneration());
        assertEquals(RebuildStatus.DONE.name(), document(MongoCollections.INDEX_STATE, MongoCollections.REBUILD).getString(MongoFields.STATUS));
        assertTrue(collections().contains(MongoCollections.batches(1)));

        collection(MongoCollections.INDEX_STATE).updateOne(Filters.eq(MongoFields.ID, MongoCollections.CURRENT),
                set(new Document(MongoFields.SWITCHED_AT, nowPlus(LONG_AGO))));
        reconcile.execute();

        assertEquals(Set.of(MongoCollections.BOOK_STATES, MongoCollections.INDEX_STATE, MongoCollections.batches(2)), collections());
    }

    private ReconcileCommand reconcileCommand() throws IOException, InterruptedException {
        int port = freePort();
        member = Hazelcast.newHazelcastInstance(member(port));
        TarantinoConfig config = TarantinoConfig.from(Map.of("TARANTINO_MONGO_URI", mongo.uri(),
                "TARANTINO_HAZELCAST", "127.0.0.1:" + port, "TARANTINO_HAZELCAST_CLUSTER", CLUSTER), TarantinoService.CONTROL);
        connections = Connections.open(config);
        awaitConnected();
        new IndexState(database()).createPointer();
        MongoIndexes.ensureStateIndexes(database());
        return ControlFactory.reconcileCommand(config, connections);
    }

    private void downloading(int bookId, int attempts) {
        bookWith(bookId, new Document(MongoFields.STATUS, BookStatus.DOWNLOADING.name())
                .append(MongoFields.CLAIM_ID, "claim-" + bookId)
                .append(MongoFields.ATTEMPTS, attempts)
                .append(MongoFields.LEASE_UNTIL, nowPlus(EXPIRED)));
    }

    private void indexing(int bookId, String batchId) {
        bookWith(bookId, new Document(MongoFields.STATUS, BookStatus.INDEXING.name())
                .append(MongoFields.BATCH_ID, new ObjectId(batchId))
                .append(MongoFields.GENERATION, 1));
    }

    private void queuedLongAgo(int bookId) {
        bookWith(bookId, new Document(MongoFields.ENQUEUED_AT, nowPlus(LONG_AGO)));
    }

    private void bookOfGeneration(int bookId, BookStatus status, int generation) {
        bookWith(bookId, new Document(MongoFields.STATUS, status.name()).append(MongoFields.GENERATION, generation));
    }

    private void bookWith(int bookId, Document fields) {
        new BookStates(database()).create(bookId);
        collection(MongoCollections.BOOK_STATES).updateOne(Filters.eq(MongoFields.ID, bookId), set(fields));
    }

    private String batch(int generation, BatchStatus status, String dateField, Duration dateFromNow) {
        ObjectId batchId = new ObjectId();
        collection(MongoCollections.batches(generation)).updateOne(Filters.eq(MongoFields.ID, batchId),
                set(new Document(MongoFields.STATUS, status.name()).append(dateField, nowPlus(dateFromNow))), new UpdateOptions().upsert(true));
        return batchId.toHexString();
    }

    private void posting(String batchId) {
        collection(MongoCollections.postings(1)).insertOne(new Document(MongoFields.TERM, "darcy")
                .append(MongoFields.BATCH_ID, new ObjectId(batchId))
                .append(MongoFields.BOOK_IDS, List.of(1342))
                .append(MongoFields.TFS, List.of(3)));
    }

    private void runRebuildTo(int target) {
        collection(MongoCollections.INDEX_STATE).updateOne(Filters.eq(MongoFields.ID, MongoCollections.REBUILD),
                set(new Document(MongoFields.TARGET_GENERATION, target)
                        .append(MongoFields.STATUS, RebuildStatus.RUNNING.name())
                        .append(MongoFields.REQUESTED_AT, NOW)
                        .append(MongoFields.SWITCHED_AT, null)), new UpdateOptions().upsert(true));
    }

    private List<Integer> enqueued(String queue) {
        return member.<String>getQueue(queue).stream()
                .map(message -> TarantinoJson.read(message, BookTask.class).bookId())
                .toList();
    }

    private String status(int bookId) {
        return state(bookId).getString(MongoFields.STATUS);
    }

    private Document state(int bookId) {
        return collection(MongoCollections.BOOK_STATES).find(Filters.eq(MongoFields.ID, bookId)).first();
    }

    private Document batchDocument(int generation, String batchId) {
        return collection(MongoCollections.batches(generation)).find(Filters.eq(MongoFields.ID, new ObjectId(batchId))).first();
    }

    private Document document(String collection, String id) {
        return collection(collection).find(Filters.eq(MongoFields.ID, id)).first();
    }

    private Set<String> collections() {
        return database().listCollectionNames().into(new HashSet<>());
    }

    private MongoCollection<Document> collection(String name) {
        return database().getCollection(name);
    }

    private MongoDatabase database() {
        return connections.database();
    }

    private void awaitConnected() throws InterruptedException {
        Instant deadline = Instant.now().plus(CONNECT_TIMEOUT);
        while (!connections.hazelcast().connected() && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }
        assertTrue(connections.hazelcast().connected(), "The Hazelcast client did not connect to the embedded member");
    }

    private static Config member(int port) {
        Config config = new Config().setClusterName(CLUSTER).setProperty("hazelcast.logging.type", "none");
        config.getNetworkConfig().setPort(port).setPortAutoIncrement(false);
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        return config;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
