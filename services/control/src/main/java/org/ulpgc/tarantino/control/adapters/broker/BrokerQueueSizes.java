package org.ulpgc.tarantino.control.adapters.broker;

import org.ulpgc.tarantino.common.messaging.Broker;
import org.ulpgc.tarantino.common.messaging.MessagingContracts;
import org.ulpgc.tarantino.control.ports.QueueSizes;

/**
 * The sizes of both queues of SPEC §4.2 in the broker of the mode, for step 4 of SPEC §5.3 and
 * {@code GET /control/status} (SPEC §6.5).
 */
public class BrokerQueueSizes implements QueueSizes {

    private final Broker broker;

    public BrokerQueueSizes(Broker broker) {
        this.broker = broker;
    }

    @Override
    public int downloads() {
        return broker.size(MessagingContracts.BOOKS_TO_DOWNLOAD);
    }

    @Override
    public int index() {
        return broker.size(MessagingContracts.BOOKS_TO_INDEX);
    }
}
