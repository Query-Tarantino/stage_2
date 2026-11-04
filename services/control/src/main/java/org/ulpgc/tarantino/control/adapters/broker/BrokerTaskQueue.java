package org.ulpgc.tarantino.control.adapters.broker;

import org.ulpgc.tarantino.common.messaging.BookTasks;
import org.ulpgc.tarantino.common.messaging.Broker;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.control.ports.DownloadQueue;
import org.ulpgc.tarantino.control.ports.IndexQueue;

/**
 * One queue of SPEC §4.2 that {@code control} sends to. Its handle is opened on the first message, so the commands can
 * be built before the broker connects, and then kept, because seeding and the reconciliation never run at once.
 */
public class BrokerTaskQueue implements DownloadQueue, IndexQueue {

    private final Broker broker;
    private final String queue;
    private BookTasks handle;

    public BrokerTaskQueue(Broker broker, String queue) {
        this.broker = broker;
        this.queue = queue;
    }

    @Override
    public void enqueue(BookTask task) {
        handle().offer(task);
    }

    private BookTasks handle() {
        if (handle == null) {
            handle = broker.queue(queue);
        }
        return handle;
    }
}
