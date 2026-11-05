package org.ulpgc.tarantino.bench.recovery;

import java.time.Duration;

/** Waits between two polls; tests pass one that only moves their clock. */
@FunctionalInterface
public interface Pause {

    void sleep(Duration duration);

    static Pause ofThread() {
        return duration -> {
            try {
                Thread.sleep(duration);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        };
    }
}
