package org.ulpgc.tarantino.common.activemq;

import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TemporaryQueue;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.transport.TransportListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.http.Health;
import org.ulpgc.tarantino.common.messaging.BatchAnnouncements;
import org.ulpgc.tarantino.common.messaging.BookTasks;
import org.ulpgc.tarantino.common.messaging.Broker;
import org.ulpgc.tarantino.common.messaging.BrokerUnavailableException;
import org.ulpgc.tarantino.common.messaging.MessagingContracts;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The broker of mode {@code activemq} (SPEC §11.2): one connection per process, started on a background thread that
 * retries every {@value #RETRY_SECONDS} s. Until it starts, {@link #connected()} is false and {@link #queue} throws, which
 * the handlers turn into {@code 503}; afterwards the failover transport reconnects on its own and a transport listener
 * keeps {@link #connected()} up to date.
 */
public final class ActiveMqBroker implements Broker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActiveMqBroker.class);
    private static final int RETRY_SECONDS = 2;
    private static final int DOWNLOAD_PREFETCH = 1;
    private static final String STATISTICS_PREFIX = "ActiveMQ.Statistics.Destination.";
    private static final String SIZE = "size";
    private static final Duration STATISTICS_TIMEOUT = Duration.ofSeconds(2);

    private final ActiveMQConnectionFactory factory;
    private final boolean persistent;
    private final int indexPrefetch;
    private final AtomicReference<Connection> connection = new AtomicReference<>();
    private final AtomicBoolean transportUp = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private ActiveMqBroker(String url, boolean persistent, int indexPrefetch) {
        this.factory = new ActiveMQConnectionFactory(url);
        this.persistent = persistent;
        this.indexPrefetch = indexPrefetch;
    }

    /** A broker that starts connecting at once, in the background; {@code indexPrefetch} is {@code TARANTINO_INDEX_BATCH}. */
    public static ActiveMqBroker connect(String url, boolean persistent, int indexPrefetch) {
        ActiveMqBroker broker = new ActiveMqBroker(url, persistent, indexPrefetch);
        Thread.ofPlatform().daemon().name("activemq-connect").start(broker::connectUntilStarted);
        return broker;
    }

    @Override
    public BookTasks queue(String name) {
        int prefetch = MessagingContracts.BOOKS_TO_DOWNLOAD.equals(name) ? DOWNLOAD_PREFETCH : indexPrefetch;
        return new ActiveMqBookTasks(started(), name, prefetch, persistent);
    }

    @Override
    public BatchAnnouncements announcements() {
        return new ActiveMqAnnouncements(started());
    }

    /**
     * The {@code size} the statistics plugin answers for the queue, on a session of its own; a {@code QueueBrowser} would
     * stop at {@code maxBrowsePageSize}. A producer on the queue first makes sure the broker has it, because the plugin
     * does not answer for a queue it does not know.
     */
    @Override
    public int size(String queue) {
        try (Session session = started().createSession(false, Session.AUTO_ACKNOWLEDGE);
             MessageProducer ensureQueue = session.createProducer(session.createQueue(queue));
             MessageProducer request = session.createProducer(session.createQueue(STATISTICS_PREFIX + queue))) {
            TemporaryQueue replies = session.createTemporaryQueue();
            MessageConsumer statistics = session.createConsumer(replies);
            Message question = session.createMessage();
            question.setJMSReplyTo(replies);
            request.send(question);
            return size(queue, (MapMessage) statistics.receive(STATISTICS_TIMEOUT.toMillis()));
        } catch (JMSException e) {
            throw ActiveMqBookTasks.unavailable(e);
        }
    }

    private static int size(String queue, MapMessage statistics) throws JMSException {
        if (statistics == null) {
            throw new BrokerUnavailableException("ActiveMQ sent no statistics for " + queue, null);
        }
        return Math.toIntExact(statistics.getLong(SIZE));
    }

    @Override
    public boolean connected() {
        return connection.get() != null && transportUp.get();
    }

    @Override
    public String name() {
        return Health.ACTIVEMQ;
    }

    @Override
    public void close() {
        closed.set(true);
        Connection started = connection.getAndSet(null);
        if (started == null) {
            return;
        }
        try {
            started.close();
        } catch (JMSException e) {
            LOGGER.warn("Could not close the ActiveMQ connection: {}", e.getMessage());
        }
    }

    Connection started() {
        Connection started = connection.get();
        if (started == null) {
            throw new BrokerUnavailableException("ActiveMQ does not answer yet", null);
        }
        return started;
    }

    private void connectUntilStarted() {
        while (!closed.get() && connection.get() == null) {
            try {
                connection.set(startedConnection());
            } catch (JMSException e) {
                LOGGER.warn("Could not connect to ActiveMQ yet, retrying in {} s: {}", RETRY_SECONDS, e.getMessage());
                pause();
            }
        }
    }

    private Connection startedConnection() throws JMSException {
        ActiveMQConnection started = (ActiveMQConnection) factory.createConnection();
        try {
            started.addTransportListener(new TransportState());
            started.start();
            transportUp.set(true);
            return started;
        } catch (JMSException e) {
            started.close();
            throw e;
        }
    }

    private static void pause() {
        try {
            Thread.sleep(Duration.ofSeconds(RETRY_SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private final class TransportState implements TransportListener {

        @Override
        public void onCommand(Object command) {
        }

        @Override
        public void onException(IOException error) {
            transportUp.set(false);
        }

        @Override
        public void transportInterupted() {
            transportUp.set(false);
        }

        @Override
        public void transportResumed() {
            transportUp.set(true);
        }
    }
}
