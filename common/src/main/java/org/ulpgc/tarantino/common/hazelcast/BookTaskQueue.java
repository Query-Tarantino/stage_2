package org.ulpgc.tarantino.common.hazelcast;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import org.ulpgc.tarantino.common.json.TarantinoJson;
import org.ulpgc.tarantino.common.messaging.BookTasks;
import org.ulpgc.tarantino.common.model.BookTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** A queue of SPEC §4.2 that carries each {@link BookTask} as {@code TarantinoJson} text. */
public final class BookTaskQueue implements BookTasks {

    private final IQueue<String> queue;

    public BookTaskQueue(HazelcastInstance client, String name) {
        this.queue = client.getQueue(name);
    }

    @Override
    public void offer(BookTask task) {
        queue.offer(TarantinoJson.write(task));
    }

    @Override
    public Optional<BookTask> poll(Duration wait) throws InterruptedException {
        return Optional.ofNullable(queue.poll(wait.toMillis(), TimeUnit.MILLISECONDS)).map(BookTaskQueue::task);
    }

    @Override
    public List<BookTask> drain(int max) {
        List<String> messages = new ArrayList<>();
        queue.drainTo(messages, max);
        return messages.stream().map(BookTaskQueue::task).toList();
    }

    @Override
    public void acknowledge() {
    }

    private static BookTask task(String message) {
        return TarantinoJson.read(message, BookTask.class);
    }
}
