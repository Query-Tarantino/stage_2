package org.ulpgc.tarantino.common.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import org.ulpgc.tarantino.common.http.Health;
import org.ulpgc.tarantino.common.messaging.BatchAnnouncements;
import org.ulpgc.tarantino.common.messaging.BookTasks;
import org.ulpgc.tarantino.common.messaging.Broker;

import java.util.function.BooleanSupplier;

/**
 * The broker of mode {@code hazelcast} (SPEC §4.2). Each queue and the topic are looked up on each use, so it can be
 * built before the client connects.
 */
public final class HazelcastBroker implements Broker {

    private final HazelcastInstance client;
    private final BooleanSupplier connected;

    public HazelcastBroker(HazelcastInstance client, BooleanSupplier connected) {
        this.client = client;
        this.connected = connected;
    }

    public static HazelcastBroker of(HazelcastConnection connection) {
        return new HazelcastBroker(connection.client(), connection::connected);
    }

    @Override
    public BookTasks queue(String name) {
        return new BookTaskQueue(client, name);
    }

    @Override
    public BatchAnnouncements announcements() {
        return new BatchPublishedTopic(client);
    }

    @Override
    public int size(String queue) {
        return client.getQueue(queue).size();
    }

    @Override
    public boolean connected() {
        return connected.getAsBoolean();
    }

    @Override
    public String name() {
        return Health.HAZELCAST;
    }

    @Override
    public void close() {
        client.shutdown();
    }
}
