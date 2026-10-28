package org.ulpgc.tarantino.crawler.commands;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.mongodb.client.MongoDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.common.hazelcast.BookTaskQueue;
import org.ulpgc.tarantino.common.hazelcast.HazelcastContracts;
import org.ulpgc.tarantino.common.model.BookErrorCode;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookState;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.mongo.BookStates;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.TemporaryMongoDatabase;
import org.ulpgc.tarantino.common.mongo.TwoAtOnce;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.crawler.adapters.datalake.batch.BatchBasedDatalakeAdapter;
import org.ulpgc.tarantino.crawler.adapters.mongo.MongoBookClaims;
import org.ulpgc.tarantino.crawler.model.failure.DownloadException;
import org.ulpgc.tarantino.crawler.ports.BookClaims;
import org.ulpgc.tarantino.crawler.ports.BookDownloader;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two workers that take the same message at once (SPEC §5.1): only one downloads it and only one enqueues it. The
 * download waits until both have tried to claim, so the loser cannot claim the book again once the winner requeues it.
 */
class DownloadBookCommandConcurrencyTest {

    private static final BookTask TASK = new BookTask(1342, Instant.parse("2026-10-21T10:05:00Z"));
    private static final String RAW = "Title: Pride and Prejudice\n*** START OF THE PROJECT GUTENBERG EBOOK PRIDE ***\nbody\n"
            + "*** END OF THE PROJECT GUTENBERG EBOOK PRIDE ***";
    private static final int DRAIN_LIMIT = 10;
    private static final int CLAIMANTS = 2;
    private static final int CLAIM_WAIT_SECONDS = 10;
    private static final Duration LEASE = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 3;

    private static HazelcastInstance member;

    @RegisterExtension
    final TemporaryMongoDatabase mongo = new TemporaryMongoDatabase();

    @TempDir
    Path datalake;

    private final BookTaskQueue downloads = new BookTaskQueue(member, HazelcastContracts.BOOKS_TO_DOWNLOAD + "-" + UUID.randomUUID());
    private final BookTaskQueue indexing = new BookTaskQueue(member, HazelcastContracts.BOOKS_TO_INDEX + "-" + UUID.randomUUID());
    private final CountDownLatch bothTriedToClaim = new CountDownLatch(CLAIMANTS);

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

    @Test
    void onlyOneOfTwoWorkersAtOnceEnqueuesAFinishedBookForIndexing() throws Exception {
        DownloadBookCommand command = command(bookId -> {
            awaitBothClaims();
            return RAW;
        });

        TwoAtOnce.call(() -> execute(command));

        assertEquals(1, indexing.drain(DRAIN_LIMIT).size());
        assertEquals(0, downloads.drain(DRAIN_LIMIT).size());
        assertEquals(BookStatus.DOWNLOADED, state().status());
    }

    @Test
    void onlyOneOfTwoWorkersAtOnceRequeuesAFailedDownload() throws Exception {
        DownloadBookCommand command = command(bookId -> {
            awaitBothClaims();
            throw new DownloadException(BookErrorCode.NETWORK_ERROR, "unreachable");
        });

        TwoAtOnce.call(() -> execute(command));

        assertEquals(1, downloads.drain(DRAIN_LIMIT).size());
        assertEquals(0, indexing.drain(DRAIN_LIMIT).size());
        BookState state = state();
        assertEquals(BookStatus.QUEUED, state.status());
        assertEquals(1, state.attempts());
    }

    private DownloadBookCommand command(BookDownloader downloader) {
        new BookStates(database()).create(1342);
        BookClaims claims = new CountedClaims(new MongoBookClaims(database(), LEASE, MAX_ATTEMPTS));
        IngestBookCommand ingest = new IngestBookCommand(downloader, new BatchBasedDatalakeAdapter(datalake));
        return new DownloadBookCommand(claims, ingest, downloads::offer, indexing::offer, new TransitionLog(), Clock.systemUTC(),
                point -> {
                }, renewal -> new RecordedLease().start(renewal));
    }

    private static Void execute(DownloadBookCommand command) {
        command.execute(TASK);
        return null;
    }

    private void awaitBothClaims() {
        try {
            if (!bothTriedToClaim.await(CLAIM_WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The other worker never tried to claim the book");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private BookState state() {
        return new BookStates(database()).state(1342).orElseThrow();
    }

    private MongoDatabase database() {
        return MongoDatabases.database(mongo.uri());
    }

    private final class CountedClaims implements BookClaims {

        private final BookClaims claims;

        private CountedClaims(BookClaims claims) {
            this.claims = claims;
        }

        @Override
        public Optional<String> claim(int bookId) {
            try {
                return claims.claim(bookId);
            } finally {
                bothTriedToClaim.countDown();
            }
        }

        @Override
        public boolean renew(int bookId, String claimId) {
            return claims.renew(bookId, claimId);
        }

        @Override
        public boolean finish(int bookId, String claimId) {
            return claims.finish(bookId, claimId);
        }

        @Override
        public Optional<BookStatus> fail(int bookId, String claimId, BookErrorCode reason) {
            return claims.fail(bookId, claimId, reason);
        }
    }
}
