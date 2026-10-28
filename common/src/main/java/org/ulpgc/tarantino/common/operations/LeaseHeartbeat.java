package org.ulpgc.tarantino.common.operations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Renews a lease every {@code TARANTINO_HEARTBEAT_SECONDS} on a thread of its own, with the “Renew” write of SPEC §5.1
 * that each service passes it. Only a renewal that does not match loses the lease; one that throws, as when Mongo does
 * not answer, is retried on the next beat. The job checks {@link #lost()} before each of its writes and closes it
 * when it ends.
 */
public final class LeaseHeartbeat implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LeaseHeartbeat.class);
    private static final String THREAD_NAME = "lease-heartbeat";

    private final BooleanSupplier renewal;
    private final ScheduledExecutorService scheduler;
    private volatile boolean lost;
    private volatile boolean closed;

    private LeaseHeartbeat(BooleanSupplier renewal, ScheduledExecutorService scheduler) {
        this.renewal = renewal;
        this.scheduler = scheduler;
    }

    /** Renews the lease every {@code interval}, the first time one interval from now; {@code renewal} answers whether it matched. */
    public static LeaseHeartbeat start(Duration interval, BooleanSupplier renewal) {
        LeaseHeartbeat heartbeat = new LeaseHeartbeat(renewal,
                Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name(THREAD_NAME).factory()));
        heartbeat.scheduler.scheduleWithFixedDelay(heartbeat::beat, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        return heartbeat;
    }

    /** Whether a renewal did not match: the job stops without writing anything else. */
    public boolean lost() {
        return lost;
    }

    @Override
    public void close() {
        closed = true;
        scheduler.shutdownNow();
    }

    void beat() {
        if (lost || closed) {
            return;
        }
        try {
            lost = !renewal.getAsBoolean();
        } catch (RuntimeException e) {
            LOGGER.warn("A lease renewal failed and is retried on the next beat: {}", e.getMessage());
        }
    }
}
