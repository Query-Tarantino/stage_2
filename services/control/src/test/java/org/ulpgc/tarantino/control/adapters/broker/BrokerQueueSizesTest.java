package org.ulpgc.tarantino.control.adapters.broker;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.hazelcast.BookTaskQueue;
import org.ulpgc.tarantino.common.hazelcast.HazelcastBroker;
import org.ulpgc.tarantino.common.messaging.MessagingContracts;
import org.ulpgc.tarantino.common.model.BookTask;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrokerQueueSizesTest {

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
        BrokerQueueSizes sizes = new BrokerQueueSizes(new HazelcastBroker(member, () -> true));
        enqueue(MessagingContracts.BOOKS_TO_DOWNLOAD, List.of(1342, 84));
        enqueue(MessagingContracts.BOOKS_TO_INDEX, List.of(11));

        assertEquals(2, sizes.downloads());
        assertEquals(1, sizes.index());
    }

    @Test
    void countsNothingInEmptyQueues() {
        BrokerQueueSizes sizes = new BrokerQueueSizes(new HazelcastBroker(member, () -> true));

        assertEquals(0, sizes.downloads());
        assertEquals(0, sizes.index());
    }

    private void enqueue(String queue, List<Integer> bookIds) {
        BookTaskQueue tasks = new BookTaskQueue(member, queue);
        bookIds.forEach(bookId -> tasks.offer(new BookTask(bookId, ENQUEUED_AT)));
    }
}
