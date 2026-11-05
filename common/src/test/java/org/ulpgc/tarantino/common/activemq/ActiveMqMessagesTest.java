package org.ulpgc.tarantino.common.activemq;

import jakarta.jms.Connection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

class ActiveMqMessagesTest {

    private static final Duration WAIT = Duration.ofSeconds(2);
    private static final Instant ENQUEUED_AT = Instant.parse("2026-10-21T10:05:00Z");
    private static final Duration PREFETCH_PAUSE = Duration.ofMillis(10);

    private final String queue = "test-" + UUID.randomUUID();
    private Connection connection;

    @BeforeEach
    void connect() throws Exception {
        connection = new ActiveMQConnectionFactory("vm://" + queue + "?broker.persistent=false").createConnection();
        connection.start();
    }

    @AfterEach
    void disconnect() throws Exception {
        connection.close();
    }

    @Test
    void carriesEachBookTaskAsItsJson() {
        ActiveMqBookTasks producer = handle();
        producer.offer(new BookTask(1342, ENQUEUED_AT));

        assertEquals(Optional.of(new BookTask(1342, ENQUEUED_AT)), handle().poll(WAIT));
    }

    @Test
    void drainsAtMostTheTasksAskedForFromThosePrefetched() throws InterruptedException {
        ActiveMqBookTasks producer = handle();
        List.of(1342, 84, 11).forEach(bookId -> producer.offer(new BookTask(bookId, ENQUEUED_AT)));
        ActiveMqBookTasks consumer = handle(5);
        consumer.poll(WAIT);

        assertEquals(List.of(84), drainOnceSomethingArrives(consumer, 1));
        assertEquals(List.of(11), drainOnceSomethingArrives(consumer, 5));
        assertEquals(List.of(), consumer.drain(5));
    }

    private static List<Integer> drainOnceSomethingArrives(ActiveMqBookTasks consumer, int max) throws InterruptedException {
        Instant deadline = Instant.now().plus(WAIT);
        List<BookTask> drained = consumer.drain(max);
        while (drained.isEmpty() && Instant.now().isBefore(deadline)) {
            Thread.sleep(PREFETCH_PAUSE);
            drained = consumer.drain(max);
        }
        return drained.stream().map(BookTask::bookId).toList();
    }

    @Test
    void aHandleThatOnlySendsNeverTakesMessages() {
        ActiveMqBookTasks sender = handle();
        sender.offer(new BookTask(1342, ENQUEUED_AT));
        sender.offer(new BookTask(84, ENQUEUED_AT));

        ActiveMqBookTasks consumer = handle();
        assertEquals(List.of(1342, 84), List.of(consumer.poll(WAIT).orElseThrow().bookId(), consumer.poll(WAIT).orElseThrow().bookId()));
    }

    @Test
    void givesAnUnacknowledgedPersistentMessageToAnotherConsumerWhenItsConsumerDies() {
        persistentHandle().offer(new BookTask(1342, ENQUEUED_AT));
        ActiveMqBookTasks dying = persistentHandle();
        assertEquals(1342, dying.poll(WAIT).orElseThrow().bookId());

        dying.close();

        assertEquals(Optional.of(new BookTask(1342, ENQUEUED_AT)), persistentHandle().poll(WAIT));
    }

    @Test
    void neverGivesAnAcknowledgedPersistentMessageAgain() {
        persistentHandle().offer(new BookTask(1342, ENQUEUED_AT));
        ActiveMqBookTasks worker = persistentHandle();
        worker.poll(WAIT);

        worker.acknowledge();
        worker.close();

        assertEquals(Optional.empty(), persistentHandle().poll(Duration.ofMillis(500)));
    }

    @Test
    void announcesEachPublishedBatchToEveryListener() throws Exception {
        ActiveMqAnnouncements announcements = new ActiveMqAnnouncements(connection);
        CompletableFuture<BatchPublished> first = new CompletableFuture<>();
        CompletableFuture<BatchPublished> second = new CompletableFuture<>();
        announcements.listen(first::complete);
        announcements.listen(second::complete);
        BatchPublished batch = new BatchPublished("6716f0c2a1b2c3d4e5f60718", 1, 100, ENQUEUED_AT);

        announcements.publish(batch);

        assertEquals(batch, first.get(5, TimeUnit.SECONDS));
        assertEquals(batch, second.get(5, TimeUnit.SECONDS));
    }

    private ActiveMqBookTasks handle() {
        return handle(1);
    }

    private ActiveMqBookTasks persistentHandle() {
        return new ActiveMqBookTasks(connection, queue, 1, true);
    }

    private ActiveMqBookTasks handle(int prefetch) {
        return new ActiveMqBookTasks(connection, queue, prefetch, false);
    }
}
