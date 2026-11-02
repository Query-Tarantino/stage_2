package org.ulpgc.tarantino.bench.throughput;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/** The books a trial handled in a window of time and how long that window lasted (SPEC §9). */
public record ThroughputWindow(int books, double seconds) {

    private static final double NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    public static ThroughputWindow between(Instant start, Instant end, int books) {
        return new ThroughputWindow(books, Duration.between(start, end).toNanos() / NANOS_PER_SECOND);
    }

    public double booksPerSecond() {
        return books / seconds;
    }
}
