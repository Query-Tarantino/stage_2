package org.ulpgc.tarantino.common.activemq;

import jakarta.jms.Connection;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.ulpgc.tarantino.common.json.TarantinoJson;
import org.ulpgc.tarantino.common.messaging.BookTasks;
import org.ulpgc.tarantino.common.messaging.BrokerUnavailableException;
import org.ulpgc.tarantino.common.model.BookTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A handle on a JMS queue of SPEC §11.2: its own session and producer, and a consumer created on its first
 * {@code poll} or {@code drain}, so a handle that only sends never takes messages. Persistent handles send persistent
 * messages and acknowledge them on {@link #acknowledge()}; the others, like an {@code IQueue}, lose a message once it
 * is received.
 */
public final class ActiveMqBookTasks implements BookTasks {

    private static final long SHORTEST_RECEIVE_MILLIS = 1;

    private final Session session;
    private final MessageProducer producer;
    private final String consumerDestination;
    private final boolean persistent;
    private MessageConsumer consumer;
    private Message lastReceived;

    public ActiveMqBookTasks(Connection connection, String queue, int prefetch, boolean persistent) {
        this.persistent = persistent;
        this.consumerDestination = queue + "?consumer.prefetchSize=" + prefetch;
        try {
            this.session = connection.createSession(false, persistent ? Session.CLIENT_ACKNOWLEDGE : Session.AUTO_ACKNOWLEDGE);
            this.producer = session.createProducer(session.createQueue(queue));
            producer.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
        } catch (JMSException e) {
            throw unavailable(e);
        }
    }

    @Override
    public void offer(BookTask task) {
        try {
            producer.send(session.createTextMessage(TarantinoJson.write(task)));
        } catch (JMSException e) {
            throw unavailable(e);
        }
    }

    @Override
    public Optional<BookTask> poll(Duration wait) {
        try {
            return task(consumer().receive(Math.max(SHORTEST_RECEIVE_MILLIS, wait.toMillis())));
        } catch (JMSException e) {
            throw unavailable(e);
        }
    }

    @Override
    public List<BookTask> drain(int max) {
        List<BookTask> tasks = new ArrayList<>();
        try {
            while (tasks.size() < max) {
                Optional<BookTask> next = task(consumer().receiveNoWait());
                if (next.isEmpty()) {
                    return tasks;
                }
                tasks.add(next.get());
            }
            return tasks;
        } catch (JMSException e) {
            throw unavailable(e);
        }
    }

    /** Acknowledges every message this session received so far, when persistent; a lost one goes to another consumer. */
    @Override
    public void acknowledge() {
        if (!persistent || lastReceived == null) {
            return;
        }
        try {
            lastReceived.acknowledge();
            lastReceived = null;
        } catch (JMSException e) {
            throw unavailable(e);
        }
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (JMSException e) {
            throw unavailable(e);
        }
    }

    private MessageConsumer consumer() throws JMSException {
        if (consumer == null) {
            consumer = session.createConsumer(session.createQueue(consumerDestination));
        }
        return consumer;
    }

    private Optional<BookTask> task(Message message) throws JMSException {
        if (message == null) {
            return Optional.empty();
        }
        lastReceived = message;
        return Optional.of(TarantinoJson.read(((TextMessage) message).getText(), BookTask.class));
    }

    static BrokerUnavailableException unavailable(JMSException cause) {
        return new BrokerUnavailableException("ActiveMQ does not answer: " + cause.getMessage(), cause);
    }
}
