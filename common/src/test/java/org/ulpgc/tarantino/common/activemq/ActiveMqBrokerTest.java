package org.ulpgc.tarantino.common.activemq;

import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.RegionBroker;
import org.apache.activemq.plugin.StatisticsBrokerPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.messaging.BookTasks;
import org.ulpgc.tarantino.common.messaging.BrokerUnavailableException;
import org.ulpgc.tarantino.common.messaging.MessagingContracts;
import org.ulpgc.tarantino.common.model.BookTask;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveMqBrokerTest {

    private static final Duration CONNECT_DEADLINE = Duration.ofSeconds(30);
    private static final Instant ENQUEUED_AT = Instant.parse("2026-10-21T10:05:00Z");

    private BrokerService server;
    private ActiveMqBroker broker;

    @AfterEach
    void stop() throws Exception {
        broker.close();
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void answersUnavailableUntilTheBrokerIsUpAndThenCarriesMessages() throws Exception {
        int port = freePort();
        broker = ActiveMqBroker.connect("failover:(tcp://localhost:" + port + ")?timeout=1000", false, 100);

        assertFalse(broker.connected());
        assertThrows(BrokerUnavailableException.class, () -> broker.queue(MessagingContracts.BOOKS_TO_DOWNLOAD));

        server = startServer(port);
        awaitConnected();
        try (BookTasks downloads = broker.queue(MessagingContracts.BOOKS_TO_DOWNLOAD)) {
            downloads.offer(new BookTask(1342, ENQUEUED_AT));
            assertEquals(Optional.of(new BookTask(1342, ENQUEUED_AT)), downloads.poll(Duration.ofSeconds(5)));
        }
        assertEquals("activemq", broker.name());
    }

    @Test
    void readsQueueSizesFromTheStatisticsPluginEvenBeyondABrowsePage() throws Exception {
        int port = freePort();
        server = startServer(port);
        broker = ActiveMqBroker.connect("failover:(tcp://localhost:" + port + ")?timeout=1000", false, 100);
        awaitConnected();

        assertEquals(0, broker.size(MessagingContracts.BOOKS_TO_INDEX));
        try (BookTasks indexing = broker.queue(MessagingContracts.BOOKS_TO_INDEX)) {
            IntStream.rangeClosed(1, 450).forEach(bookId -> indexing.offer(new BookTask(bookId, ENQUEUED_AT)));
        }

        assertEquals(450, broker.size(MessagingContracts.BOOKS_TO_INDEX));
    }

    @Test
    void leavesNoTemporaryQueueBehindAfterReadingSizes() throws Exception {
        int port = freePort();
        server = startServer(port);
        broker = ActiveMqBroker.connect("failover:(tcp://localhost:" + port + ")?timeout=1000", false, 100);
        awaitConnected();

        IntStream.range(0, 5).forEach(read -> broker.size(MessagingContracts.BOOKS_TO_DOWNLOAD));

        assertEquals(0, ((RegionBroker) server.getRegionBroker()).getTempQueueRegion().getDestinationMap().size());
    }

    static BrokerService startServer(int port) throws Exception {
        BrokerService service = new BrokerService();
        service.setPersistent(false);
        service.setUseJmx(false);
        service.setBrokerName("test-" + port);
        service.setPlugins(new BrokerPlugin[]{new StatisticsBrokerPlugin()});
        service.addConnector("tcp://localhost:" + port);
        service.start();
        return service;
    }

    static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private void awaitConnected() throws InterruptedException {
        Instant deadline = Instant.now().plus(CONNECT_DEADLINE);
        while (!broker.connected()) {
            assertTrue(Instant.now().isBefore(deadline), "The broker never connected");
            Thread.sleep(100);
        }
    }
}
