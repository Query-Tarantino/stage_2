package org.ulpgc.tarantino.common.hazelcast;

import com.hazelcast.core.HazelcastInstance;

/** A client of the cluster and whether it is connected right now, which {@code /health} reports (SPEC §6.5). */
public final class HazelcastConnection {

    private final HazelcastInstance client;
    private final ConnectionState state;

    HazelcastConnection(HazelcastInstance client, ConnectionState state) {
        this.client = client;
        this.state = state;
    }

    public HazelcastInstance client() {
        return client;
    }

    public boolean connected() {
        return state.connected();
    }
}
