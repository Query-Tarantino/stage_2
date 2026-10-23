package org.ulpgc.tarantino.crawler.benchmarking.support.datalake;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.crawler.adapters.datalake.time.TimeBasedDatalakeAdapter;
import org.ulpgc.tarantino.crawler.benchmarking.support.files.Directories;
import org.ulpgc.tarantino.crawler.model.book.BookText;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrawlClockTest {

    private static final Instant START = Instant.parse("2025-09-25T23:00:00Z");

    @TempDir
    Path root;

    @Test
    void advancesOneHourEveryHundredBooks() {
        assertEquals(START, CrawlClock.instantOf(START, 99));
        assertEquals(START.plus(Duration.ofHours(1)), CrawlClock.instantOf(START, 100));
        assertEquals(Duration.ofHours(3), CrawlClock.durationOf(250));
    }

    @Test
    void spreadsTheTimeLayoutOverOneHourDirectoryPerHundredBooks() {
        CrawlClock clock = new CrawlClock(START);
        TimeBasedDatalakeAdapter datalake = new TimeBasedDatalakeAdapter(root, clock);
        for (int position = 0; position < 250; position++) {
            clock.moveTo(position);
            datalake.save(new BookText(position + 1, "header", "body"));
        }

        // 20250925/23, 20250926/00 and 20250926/01, plus their two day directories
        assertEquals(5, Directories.footprint(root).directories());
    }
}
