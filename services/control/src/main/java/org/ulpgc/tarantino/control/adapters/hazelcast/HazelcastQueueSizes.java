package org.ulpgc.tarantino.control.adapters.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import org.ulpgc.tarantino.common.hazelcast.HazelcastContracts;
import org.ulpgc.tarantino.control.ports.QueueSizes;

/**
 * The sizes of both queues of SPEC §4.2, for step 4 of SPEC §5.3 and {@code GET /control/status} (SPEC §6.5). It looks
 * the queues up on every call, so it can be built before the client connects.
 */
public class HazelcastQueueSizes implements QueueSizes {

    private final HazelcastInstance client;

    public HazelcastQueueSizes(HazelcastInstance client) {
        this.client = client;
    }

    @Override
    public int downloads() {
        return client.getQueue(HazelcastContracts.BOOKS_TO_DOWNLOAD).size();
    }

    @Override
    public int index() {
        return client.getQueue(HazelcastContracts.BOOKS_TO_INDEX).size();
    }
}
