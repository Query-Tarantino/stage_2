package org.ulpgc.tarantino.common.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;
import org.ulpgc.tarantino.common.json.TarantinoJson;
import org.ulpgc.tarantino.common.messaging.BatchAnnouncements;
import org.ulpgc.tarantino.common.messaging.MessagingContracts;
import org.ulpgc.tarantino.common.model.BatchPublished;

import java.util.function.Consumer;

/** The {@code published-batches} topic of SPEC §4.2, carrying each {@link BatchPublished} as {@code TarantinoJson} text. */
public final class BatchPublishedTopic implements BatchAnnouncements {

    private final ITopic<String> topic;

    public BatchPublishedTopic(HazelcastInstance client) {
        this.topic = client.getTopic(MessagingContracts.PUBLISHED_BATCHES);
    }

    @Override
    public void publish(BatchPublished batch) {
        topic.publish(TarantinoJson.write(batch));
    }

    @Override
    public void listen(Consumer<BatchPublished> listener) {
        topic.addMessageListener(message -> listener.accept(TarantinoJson.read(message.getMessageObject(), BatchPublished.class)));
    }
}
