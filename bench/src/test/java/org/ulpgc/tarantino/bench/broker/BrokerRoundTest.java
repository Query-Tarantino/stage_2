package org.ulpgc.tarantino.bench.broker;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.messaging.BatchAnnouncements;
import org.ulpgc.tarantino.common.messaging.BookTasks;
import org.ulpgc.tarantino.common.messaging.Broker;
import org.ulpgc.tarantino.common.model.BookTask;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrokerRoundTest {

    private static final String QUEUE = "bench-broker-test";
    private static final int LEFT_BY_AN_EARLIER_ROUND = 1_000_005;

    private final InMemoryBroker broker = new InMemoryBroker();

    @Test
    void countsOnlyTheMessagesItSent() throws InterruptedException {
        broker.queue(QUEUE).offer(new BookTask(LEFT_BY_AN_EARLIER_ROUND, Instant.now()));

        Map<Integer, BrokerSample> received = new BrokerRound(broker, QUEUE, 2).run(0, 10, Duration.ZERO);

        assertEquals(IntStream.range(0, 10).boxed().collect(Collectors.toSet()), received.keySet());
    }

    private static final class InMemoryBroker implements Broker {

        private final Map<String, BlockingQueue<BookTask>> queues = new ConcurrentHashMap<>();

        @Override
        public BookTasks queue(String name) {
            BlockingQueue<BookTask> tasks = queues.computeIfAbsent(name, key -> new LinkedBlockingQueue<>());
            return new BookTasks() {
                @Override
                public void offer(BookTask task) {
                    tasks.add(task);
                }

                @Override
                public Optional<BookTask> poll(Duration wait) throws InterruptedException {
                    return Optional.ofNullable(tasks.poll(wait.toMillis(), TimeUnit.MILLISECONDS));
                }

                @Override
                public List<BookTask> drain(int max) {
                    List<BookTask> drained = new ArrayList<>();
                    tasks.drainTo(drained, max);
                    return drained;
                }

                @Override
                public void acknowledge() {
                }
            };
        }

        @Override
        public BatchAnnouncements announcements() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size(String queue) {
            return queues.getOrDefault(queue, new LinkedBlockingQueue<>()).size();
        }

        @Override
        public boolean connected() {
            return true;
        }

        @Override
        public String name() {
            return "in-memory";
        }

        @Override
        public void close() {
        }
    }
}
