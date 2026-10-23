package org.ulpgc.tarantino.common.operations;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.config.CrashPoint;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrashPointsTest {

    private final AtomicInteger halts = new AtomicInteger();

    @Test
    void haltsOnlyAtTheConfiguredPoint() {
        CrashPoints crashPoints = new CrashPoints(Optional.of(CrashPoint.AFTER_CLAIM), halts::incrementAndGet);

        crashPoints.hit(CrashPoint.CRAWLER_AFTER_DEQUEUE);
        assertEquals(0, halts.get());

        crashPoints.hit(CrashPoint.AFTER_CLAIM);
        assertEquals(1, halts.get());
    }

    @Test
    void neverHaltsWithoutAConfiguredPoint() {
        CrashPoints crashPoints = new CrashPoints(Optional.empty(), halts::incrementAndGet);

        for (CrashPoint point : CrashPoint.values()) {
            crashPoints.hit(point);
        }

        assertEquals(0, halts.get());
    }
}
