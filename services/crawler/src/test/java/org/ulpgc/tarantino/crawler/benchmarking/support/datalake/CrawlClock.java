package org.ulpgc.tarantino.crawler.benchmarking.support.datalake;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** The clock of a crawl that downloads 100 books per hour: the book at position i is saved at start + ⌊i / 100⌋ hours. */
public final class CrawlClock extends Clock {

    private static final int BOOKS_PER_HOUR = 100;

    private final Instant start;
    private int position;

    public CrawlClock(Instant start) {
        this.start = start;
    }

    public static Instant instantOf(Instant start, int position) {
        return start.plus(Duration.ofHours(position / BOOKS_PER_HOUR));
    }

    public static Duration durationOf(int books) {
        return Duration.ofHours((books + BOOKS_PER_HOUR - 1) / BOOKS_PER_HOUR);
    }

    public void moveTo(int position) {
        this.position = position;
    }

    @Override
    public Instant instant() {
        return instantOf(start, position);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant(), zone);
    }
}
