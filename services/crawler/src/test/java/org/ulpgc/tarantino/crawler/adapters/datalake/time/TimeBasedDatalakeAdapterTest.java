package org.ulpgc.tarantino.crawler.adapters.datalake.time;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.crawler.model.book.BookText;
import org.ulpgc.tarantino.crawler.model.book.StoredPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeBasedDatalakeAdapterTest {

    @TempDir
    Path root;

    private final Clock clock = Clock.fixed(Instant.parse("2025-09-25T14:30:00Z"), ZoneOffset.UTC);

    @Test
    void storesBooksUnderDayAndHourDirectories() throws IOException {
        StoredPaths paths = new TimeBasedDatalakeAdapter(root, clock).save(new BookText(5, "header", "body"));

        assertEquals(root.resolve("20250925/14/5.header.txt"), paths.header());
        assertEquals(root.resolve("20250925/14/5.body.txt"), paths.body());
        assertEquals("header", Files.readString(paths.header()));
        assertEquals("body", Files.readString(paths.body()));
    }

    @Test
    void findsPathsOfStoredBooksOnly() {
        TimeBasedDatalakeAdapter datalake = new TimeBasedDatalakeAdapter(root, clock);
        StoredPaths stored = datalake.save(new BookText(5, "header", "body"));

        assertEquals(Optional.of(stored), datalake.pathsOf(5));
        assertTrue(datalake.pathsOf(6).isEmpty());
    }

    @Test
    void findsBooksSavedInEarlierHours() {
        StoredPaths earlier = new TimeBasedDatalakeAdapter(root, clock).save(new BookText(5, "header", "body"));
        Clock later = Clock.offset(clock, Duration.ofDays(1));

        assertEquals(Optional.of(earlier), new TimeBasedDatalakeAdapter(root, later).pathsOf(5));
    }

    @Test
    void leavesNoTemporaryFilesBehind() throws IOException {
        new TimeBasedDatalakeAdapter(root, clock).save(new BookText(5, "header", "body"));

        try (Stream<Path> files = Files.walk(root)) {
            assertTrue(files.noneMatch(path -> path.toString().endsWith(".tmp")));
        }
    }
}
