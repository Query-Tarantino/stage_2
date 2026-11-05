package org.ulpgc.tarantino.common.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.model.BatchPublished;
import org.ulpgc.tarantino.common.model.BookTask;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HazelcastMessagesTest {

    private static final Instant ENQUEUED_AT = Instant.parse("2026-10-21T10:05:00Z");

    private static HazelcastInstance member;

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
    void carriesBookTasksAsJsonText() throws InterruptedException {
        String name = "queue-" + UUID.randomUUID();
        BookTaskQueue queue = new BookTaskQueue(member, name);

        queue.offer(new BookTask(1342, ENQUEUED_AT));

        assertEquals("{\"book_id\":1342,\"enqueued_at\":\"2026-10-21T10:05:00Z\"}", member.getQueue(name).peek());
        assertEquals(Optional.of(new BookTask(1342, ENQUEUED_AT)), queue.poll(Duration.ofSeconds(1)));
    }

    @Test
    void drainsOnlyWhatIsWaitingUpToTheMaximum() {
        BookTaskQueue queue = new BookTaskQueue(member, "queue-" + UUID.randomUUID());
        List.of(1342, 84, 11).forEach(bookId -> queue.offer(new BookTask(bookId, ENQUEUED_AT)));

        assertEquals(List.of(new BookTask(1342, ENQUEUED_AT), new BookTask(84, ENQUEUED_AT)), queue.drain(2));
        assertEquals(List.of(new BookTask(11, ENQUEUED_AT)), queue.drain(2));
    }

    @Test
    void announcesPublishedBatchesToItsListeners() throws Exception {
        BatchPublishedTopic topic = new BatchPublishedTopic(member);
        CompletableFuture<BatchPublished> received = new CompletableFuture<>();
        topic.listen(received::complete);
        BatchPublished batch = new BatchPublished("6716f0c2a1b2c3d4e5f60718", 1, 100, ENQUEUED_AT);

        topic.publish(batch);

        assertEquals(batch, received.get(5, TimeUnit.SECONDS));
    }
}
