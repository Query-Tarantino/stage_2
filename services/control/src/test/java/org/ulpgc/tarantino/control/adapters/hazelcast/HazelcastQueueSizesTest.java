package org.ulpgc.tarantino.control.adapters.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.hazelcast.BookTaskQueue;
import org.ulpgc.tarantino.common.hazelcast.HazelcastContracts;
import org.ulpgc.tarantino.common.model.BookTask;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HazelcastQueueSizesTest {

    private static final Instant ENQUEUED_AT = Instant.parse("2026-10-21T10:05:00Z");

    private HazelcastInstance member;

    @BeforeEach
    void startMember() {
        Config config = new Config().setClusterName("test-" + UUID.randomUUID()).setProperty("hazelcast.logging.type", "none");
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        member = Hazelcast.newHazelcastInstance(config);
    }

    @AfterEach
    void stopMember() {
        member.shutdown();
    }

    @Test
    void countsTheMessagesWaitingInEachQueue() {
        HazelcastQueueSizes sizes = new HazelcastQueueSizes(member);
        enqueue(HazelcastContracts.BOOKS_TO_DOWNLOAD, List.of(1342, 84));
        enqueue(HazelcastContracts.BOOKS_TO_INDEX, List.of(11));

        assertEquals(2, sizes.downloads());
        assertEquals(1, sizes.index());
    }

    @Test
    void countsNothingInEmptyQueues() {
        HazelcastQueueSizes sizes = new HazelcastQueueSizes(member);

        assertEquals(0, sizes.downloads());
        assertEquals(0, sizes.index());
    }

    private void enqueue(String queue, List<Integer> bookIds) {
        BookTaskQueue tasks = new BookTaskQueue(member, queue);
        bookIds.forEach(bookId -> tasks.offer(new BookTask(bookId, ENQUEUED_AT)));
    }
}
