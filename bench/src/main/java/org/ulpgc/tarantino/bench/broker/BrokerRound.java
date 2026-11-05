package org.ulpgc.tarantino.bench.broker;

import org.ulpgc.tarantino.common.messaging.BookTasks;
import org.ulpgc.tarantino.common.messaging.Broker;
import org.ulpgc.tarantino.common.model.BookTask;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;

/**
 * Sends messages to a queue of its own, named after {@code bench-broker}, and receives them on {@code consumers} threads
 * of the same JVM, each with its own handle, keeping the send and receive {@code System.nanoTime()} of each message by
 * its id. A message it did not send, such as one a consumer of an earlier round had prefetched when it closed, is not
 * counted.
 */
final class BrokerRound {

    static final String QUEUE = "bench-broker";

    private static final Duration POLL_WAIT = Duration.ofMillis(100);
    private static final Duration DRAIN_DEADLINE = Duration.ofSeconds(30);

    private final Broker broker;
    private final String queue;
    private final int consumers;
    private final Map<Integer, Long> sentAt = new ConcurrentHashMap<>();
    private final Map<Integer, BrokerSample> received = new ConcurrentHashMap<>();
    private final AtomicBoolean receiving = new AtomicBoolean(true);
    private long startNanos;

    BrokerRound(Broker broker, String queue, int consumers) {
        this.broker = broker;
        this.queue = queue;
        this.consumers = consumers;
    }

    /** Sends {@code count} messages from {@code firstId}, one every {@code interval}, or as fast as possible with zero. */
    Map<Integer, BrokerSample> run(int firstId, int count, Duration interval) throws InterruptedException {
        try (ExecutorService threads = Executors.newFixedThreadPool(consumers)) {
            IntStream.range(0, consumers).forEach(consumer -> threads.execute(() -> receive(consumer)));
            send(firstId, count, interval);
            awaitAll(count);
            receiving.set(false);
        }
        return Map.copyOf(received);
    }

    private void send(int firstId, int count, Duration interval) {
        try (BookTasks producer = broker.queue(queue)) {
            startNanos = System.nanoTime();
            for (int index = 0; index < count; index++) {
                waitUntil(startNanos + index * interval.toNanos());
                int messageId = firstId + index;
                sentAt.put(messageId, System.nanoTime());
                producer.offer(new BookTask(messageId, Instant.now()));
            }
        }
    }

    private void receive(int consumer) {
        try (BookTasks handle = broker.queue(queue)) {
            while (receiving.get()) {
                handle.poll(POLL_WAIT)
                        .filter(task -> sentAt.containsKey(task.bookId()))
                        .ifPresent(task -> received.put(task.bookId(), new BrokerSample(sentAt.get(task.bookId()), System.nanoTime(), consumer)));
                handle.acknowledge();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitAll(int count) throws InterruptedException {
        long deadline = System.nanoTime() + DRAIN_DEADLINE.toNanos();
        while (received.size() < count && System.nanoTime() < deadline) {
            Thread.sleep(POLL_WAIT);
        }
    }

    private static void waitUntil(long nanos) {
        long remaining = nanos - System.nanoTime();
        while (remaining > 0) {
            LockSupport.parkNanos(remaining);
            remaining = nanos - System.nanoTime();
        }
    }

    /** When the first message was sent. */
    long startNanos() {
        return startNanos;
    }

    static List<BrokerSample> sentAfter(Map<Integer, BrokerSample> samples, long firstKeptNanos) {
        return samples.values().stream().filter(sample -> sample.sentNanos() >= firstKeptNanos).toList();
    }
}
