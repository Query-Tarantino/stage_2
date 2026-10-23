package org.ulpgc.tarantino.common.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;
import org.ulpgc.tarantino.common.json.TarantinoJson;
import org.ulpgc.tarantino.common.model.BatchPublished;

import java.util.function.Consumer;

/** The {@code published-batches} topic of SPEC §4.2, carrying each {@link BatchPublished} as {@code TarantinoJson} text. */
public final class BatchPublishedTopic {

    private final ITopic<String> topic;

    public BatchPublishedTopic(HazelcastInstance client) {
        this.topic = client.getTopic(HazelcastContracts.PUBLISHED_BATCHES);
    }

    public void publish(BatchPublished batch) {
        topic.publish(TarantinoJson.write(batch));
    }

    public void listen(Consumer<BatchPublished> listener) {
        topic.addMessageListener(message -> listener.accept(TarantinoJson.read(message.getMessageObject(), BatchPublished.class)));
    }
}
