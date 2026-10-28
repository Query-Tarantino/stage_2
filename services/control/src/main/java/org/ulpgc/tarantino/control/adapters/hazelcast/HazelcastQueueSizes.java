package org.ulpgc.tarantino.control.adapters.hazelcast;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import org.ulpgc.tarantino.common.hazelcast.HazelcastContracts;
import org.ulpgc.tarantino.control.ports.QueueSizes;

/** The sizes of both queues of SPEC §4.2, for step 4 of SPEC §5.3 and {@code GET /control/status} (SPEC §6.5). */
public class HazelcastQueueSizes implements QueueSizes {

    private final IQueue<String> downloads;
    private final IQueue<String> index;

    public HazelcastQueueSizes(HazelcastInstance client) {
        this.downloads = client.getQueue(HazelcastContracts.BOOKS_TO_DOWNLOAD);
        this.index = client.getQueue(HazelcastContracts.BOOKS_TO_INDEX);
    }

    @Override
    public int downloads() {
        return downloads.size();
    }

    @Override
    public int index() {
        return index.size();
    }
}
