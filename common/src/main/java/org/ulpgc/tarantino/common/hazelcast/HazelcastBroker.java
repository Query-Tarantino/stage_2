package org.ulpgc.tarantino.common.hazelcast;

import org.ulpgc.tarantino.common.http.Health;
import org.ulpgc.tarantino.common.messaging.BatchAnnouncements;
import org.ulpgc.tarantino.common.messaging.BookTasks;
import org.ulpgc.tarantino.common.messaging.Broker;

/**
 * The broker of mode {@code hazelcast} (SPEC §4.2). Each queue and the topic are looked up on each use, so it can be
 * built before the client connects.
 */
public final class HazelcastBroker implements Broker {

    private final HazelcastConnection connection;

    public HazelcastBroker(HazelcastConnection connection) {
        this.connection = connection;
    }

    @Override
    public BookTasks queue(String name) {
        return new BookTaskQueue(connection.client(), name);
    }

    @Override
    public BatchAnnouncements announcements() {
        return new BatchPublishedTopic(connection.client());
    }

    @Override
    public int size(String queue) {
        return connection.client().getQueue(queue).size();
    }

    @Override
    public boolean connected() {
        return connection.connected();
    }

    @Override
    public String name() {
        return Health.HAZELCAST;
    }
}
