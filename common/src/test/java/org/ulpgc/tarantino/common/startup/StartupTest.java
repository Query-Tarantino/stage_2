package org.ulpgc.tarantino.common.startup;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StartupTest {

    @Test
    void retriesAStepUntilItWorks() {
        AtomicInteger attempts = new AtomicInteger();

        Startup.retry("reach Mongo", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("Mongo does not answer");
            }
        }, Duration.ofMillis(1));

        assertEquals(3, attempts.get());
    }
}
