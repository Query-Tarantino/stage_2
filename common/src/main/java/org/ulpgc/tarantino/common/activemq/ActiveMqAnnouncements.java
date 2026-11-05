package org.ulpgc.tarantino.common.activemq;

import jakarta.jms.Connection;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.ulpgc.tarantino.common.json.TarantinoJson;
import org.ulpgc.tarantino.common.messaging.BatchAnnouncements;
import org.ulpgc.tarantino.common.messaging.MessagingContracts;
import org.ulpgc.tarantino.common.model.BatchPublished;

import java.util.function.Consumer;

/**
 * The {@code published-batches} JMS topic of SPEC §11.2: non-persistent and non-durable, so like an {@code ITopic} a
 * replica only hears what is published while it listens.
 */
public final class ActiveMqAnnouncements implements BatchAnnouncements {

    private final Connection connection;

    public ActiveMqAnnouncements(Connection connection) {
        this.connection = connection;
    }

    /** On a session of its own, so the announcer needs no handle shared between threads. */
    @Override
    public void publish(BatchPublished batch) {
        try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
             MessageProducer producer = session.createProducer(session.createTopic(MessagingContracts.PUBLISHED_BATCHES))) {
            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
            producer.send(session.createTextMessage(TarantinoJson.write(batch)));
        } catch (JMSException e) {
            throw ActiveMqBookTasks.unavailable(e);
        }
    }

    /** One listener on a session that stays open for the life of the process. */
    @Override
    public void listen(Consumer<BatchPublished> listener) {
        try {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer consumer = session.createConsumer(session.createTopic(MessagingContracts.PUBLISHED_BATCHES));
            consumer.setMessageListener(message -> listener.accept(batch((TextMessage) message)));
        } catch (JMSException e) {
            throw ActiveMqBookTasks.unavailable(e);
        }
    }

    private static BatchPublished batch(TextMessage message) {
        try {
            return TarantinoJson.read(message.getText(), BatchPublished.class);
        } catch (JMSException e) {
            throw ActiveMqBookTasks.unavailable(e);
        }
    }
}
