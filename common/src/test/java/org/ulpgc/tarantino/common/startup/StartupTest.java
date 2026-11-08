package org.ulpgc.tarantino.common.startup;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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

    @Test
    void exitsWithCodeOneWhenTheStartingThreadDies() throws InterruptedException {
        List<Integer> exitCodes = new CopyOnWriteArrayList<>();
        Thread main = Thread.ofPlatform().start(() -> {
            Startup.exitIfThisThreadFails(exitCodes::add);
            throw new IllegalStateException("No stopwords.txt");
        });
        main.join();

        assertEquals(List.of(1), exitCodes);
    }
}
