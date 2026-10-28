package org.ulpgc.tarantino.common.operations;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaseHeartbeatTest {

    private static final Duration NEVER_DURING_THE_TEST = Duration.ofHours(1);

    private final AtomicInteger renewals = new AtomicInteger();

    @Test
    void losesTheLeaseOnlyWhenARenewalDoesNotMatch() {
        try (LeaseHeartbeat heartbeat = LeaseHeartbeat.start(NEVER_DURING_THE_TEST, answering(true, true, false))) {
            heartbeat.beat();
            heartbeat.beat();
            assertFalse(heartbeat.lost());

            heartbeat.beat();
            assertTrue(heartbeat.lost());
        }
    }

    @Test
    void retriesARenewalThatThrowsOnTheNextBeat() {
        BooleanSupplier failsOnce = () -> {
            if (renewals.incrementAndGet() == 1) {
                throw new IllegalStateException("Mongo does not answer");
            }
            return true;
        };
        try (LeaseHeartbeat heartbeat = LeaseHeartbeat.start(NEVER_DURING_THE_TEST, failsOnce)) {
            heartbeat.beat();
            assertFalse(heartbeat.lost());

            heartbeat.beat();
            assertFalse(heartbeat.lost());
            assertEquals(2, renewals.get());
        }
    }

    @Test
    void stopsRenewingOnceTheLeaseIsLost() {
        try (LeaseHeartbeat heartbeat = LeaseHeartbeat.start(NEVER_DURING_THE_TEST, answering(false, true))) {
            heartbeat.beat();
            heartbeat.beat();

            assertTrue(heartbeat.lost());
            assertEquals(1, renewals.get());
        }
    }

    @Test
    void neverRenewsOnceClosed() {
        LeaseHeartbeat heartbeat = LeaseHeartbeat.start(NEVER_DURING_THE_TEST, answering(true));
        heartbeat.close();

        heartbeat.beat();

        assertEquals(0, renewals.get());
    }

    @Test
    void renewsOnItsOwnThreadEveryInterval() throws InterruptedException {
        CountDownLatch threeRenewals = new CountDownLatch(3);
        try (LeaseHeartbeat heartbeat = LeaseHeartbeat.start(Duration.ofMillis(10), () -> {
            threeRenewals.countDown();
            return true;
        })) {
            assertTrue(threeRenewals.await(5, TimeUnit.SECONDS));
            assertFalse(heartbeat.lost());
        }
    }

    private BooleanSupplier answering(Boolean... matches) {
        Queue<Boolean> answers = new ArrayDeque<>(List.of(matches));
        return () -> {
            renewals.incrementAndGet();
            return answers.remove();
        };
    }
}
