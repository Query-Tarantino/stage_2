package org.ulpgc.tarantino.bench.recovery;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock that only moves when a {@link Pause} sleeps, so polling loops run at once in tests. */
final class MovingClock extends Clock {

    private Instant now;

    MovingClock(Instant start) {
        this.now = start;
    }

    Pause pause() {
        return this::advance;
    }

    void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
