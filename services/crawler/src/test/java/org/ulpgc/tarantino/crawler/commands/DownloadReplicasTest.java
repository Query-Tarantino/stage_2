package org.ulpgc.tarantino.crawler.commands;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.mongodb.client.MongoDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ulpgc.tarantino.common.datalake.DatalakePaths;
import org.ulpgc.tarantino.common.hazelcast.BookTaskQueue;
import org.ulpgc.tarantino.common.hazelcast.HazelcastContracts;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.mongo.BookStates;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;
import org.ulpgc.tarantino.common.operations.LeaseHeartbeat;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.crawler.adapters.datalake.batch.BatchBasedDatalakeAdapter;
import org.ulpgc.tarantino.crawler.adapters.mongo.MongoBookClaims;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two and three crawler replicas on the same datalake, each with its own workers, take every message of
 * {@code books-to-download} twice: each book ends {@code DOWNLOADED} once, is enqueued for indexing once and leaves no
 * {@code .tmp} behind (SPEC §4.6 and §5.1).
 */
class DownloadReplicasTest {

    private static final List<Integer> BOOK_IDS = IntStream.rangeClosed(1, 20).map(index -> index * 97).boxed().toList();
    private static final int WORKERS_PER_REPLICA = 4;
    private static final int COPIES_OF_EACH_MESSAGE = 2;
    private static final Duration LEASE = Duration.ofSeconds(60);
    private static final Duration HEARTBEAT = Duration.ofSeconds(20);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration DOWNLOAD_DEADLINE = Duration.ofSeconds(60);
    private static final Duration POLL_PAUSE = Duration.ofMillis(200);
    private static final int DRAIN_LIMIT = 1_000;

    private static HazelcastInstance member;

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @TempDir
    Path datalake;

    private final BookTaskQueue downloads = new BookTaskQueue(member, HazelcastContracts.BOOKS_TO_DOWNLOAD + "-" + UUID.randomUUID());
    private final BookTaskQueue indexing = new BookTaskQueue(member, HazelcastContracts.BOOKS_TO_INDEX + "-" + UUID.randomUUID());

    @BeforeAll
    static void startMember() {
        Config config = new Config().setClusterName("test-" + UUID.randomUUID()).setProperty("hazelcast.logging.type", "none");
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        member = Hazelcast.newHazelcastInstance(config);
    }

    @AfterAll
    static void stopMember() {
        member.shutdown();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void everyBookEndsDownloadedOnceWithoutLeftoverTemporaryFiles(int replicas) throws Exception {
        seedEveryBookTwice();

        List<Thread> workers = IntStream.range(0, replicas)
                .boxed()
                .flatMap(replica -> startReplica())
                .toList();
        awaitEveryBookDownloaded();
        stop(workers);

        Map<Integer, Long> indexMessagesPerBook = indexing.drain(DRAIN_LIMIT).stream()
                .collect(Collectors.groupingBy(BookTask::bookId, Collectors.counting()));
        assertEquals(BOOK_IDS.stream().collect(Collectors.toMap(Function.identity(), bookId -> 1L)), indexMessagesPerBook);
        assertEquals(List.of(), temporaryFiles());
        DatalakePaths paths = new DatalakePaths(datalake);
        assertTrue(BOOK_IDS.stream().allMatch(bookId -> Files.exists(paths.body(bookId))));
    }

    private void seedEveryBookTwice() {
        BookStates states = new BookStates(database());
        BOOK_IDS.forEach(states::create);
        IntStream.range(0, COPIES_OF_EACH_MESSAGE)
                .forEach(copy -> BOOK_IDS.forEach(bookId -> downloads.offer(new BookTask(bookId, Instant.now()))));
    }

    private Stream<Thread> startReplica() {
        MongoBookClaims claims = new MongoBookClaims(database(), LEASE, MAX_ATTEMPTS);
        IngestBookCommand ingest = new IngestBookCommand(DownloadReplicasTest::gutenbergText, new BatchBasedDatalakeAdapter(datalake));
        DownloadBookCommand download = new DownloadBookCommand(claims, ingest, downloads::offer, indexing::offer, new TransitionLog(),
                Clock.systemUTC(), point -> {
                }, renewal -> LeaseHeartbeat.start(HEARTBEAT, renewal));
        DownloadWorker worker = new DownloadWorker(downloads::poll, download, point -> {
        });
        return IntStream.range(0, WORKERS_PER_REPLICA).mapToObj(index -> Thread.ofPlatform().start(worker));
    }

    private void awaitEveryBookDownloaded() throws InterruptedException {
        BookStates states = new BookStates(database());
        Instant deadline = Instant.now().plus(DOWNLOAD_DEADLINE);
        while (!BOOK_IDS.stream().allMatch(bookId -> states.state(bookId).orElseThrow().status() == BookStatus.DOWNLOADED)) {
            assertTrue(Instant.now().isBefore(deadline), "Not every book was downloaded in time");
            Thread.sleep(POLL_PAUSE);
        }
    }

    private static void stop(List<Thread> workers) throws InterruptedException {
        workers.forEach(Thread::interrupt);
        for (Thread worker : workers) {
            worker.join();
        }
    }

    private List<Path> temporaryFiles() throws IOException {
        try (Stream<Path> files = Files.walk(datalake)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".tmp")).toList();
        }
    }

    private static String gutenbergText(int bookId) {
        return "Title: Book " + bookId + "\n*** START OF THE PROJECT GUTENBERG EBOOK " + bookId + " ***\nbody of " + bookId
                + "\n*** END OF THE PROJECT GUTENBERG EBOOK " + bookId + " ***";
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }
}
