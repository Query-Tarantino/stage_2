package org.ulpgc.tarantino.crawler.adapters.datalake;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.crawler.adapters.datalake.batch.BatchBasedDatalakeAdapter;
import org.ulpgc.tarantino.crawler.adapters.datalake.book.BookBasedDatalakeAdapter;
import org.ulpgc.tarantino.crawler.adapters.datalake.time.TimeBasedDatalakeAdapter;
import org.ulpgc.tarantino.crawler.model.book.BookText;
import org.ulpgc.tarantino.crawler.ports.DatalakeStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NewBooksDetectionTest {

    private static final Instant THRESHOLD = Instant.parse("2025-09-25T15:30:00Z");

    @TempDir
    Path root;

    @Test
    void timeBasedLayoutSelectsHourDirectoriesFromTheHourOfTheInstant() {
        saveAt(1, "2025-09-25T14:59:00Z");
        saveAt(2, "2025-09-25T15:10:00Z");
        saveAt(3, "2025-09-26T09:00:00Z");

        assertEquals(Set.of(2, 3), new TimeBasedDatalakeAdapter(root).idsStoredSince(THRESHOLD));
    }

    @Test
    void bookBasedLayoutSelectsBodiesModifiedSinceTheInstant() throws IOException {
        assertOnlyRecentBooksDetected(new BookBasedDatalakeAdapter(root));
    }

    @Test
    void batchBasedLayoutSelectsBodiesModifiedSinceTheInstant() throws IOException {
        assertOnlyRecentBooksDetected(new BatchBasedDatalakeAdapter(root));
    }

    @Test
    void emptyDatalakesDetectNothing() {
        assertEquals(Set.of(), new BookBasedDatalakeAdapter(root.resolve("missing")).idsStoredSince(THRESHOLD));
        assertEquals(Set.of(), new TimeBasedDatalakeAdapter(root.resolve("missing")).idsStoredSince(THRESHOLD));
    }

    private void saveAt(int bookId, String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        new TimeBasedDatalakeAdapter(root, clock).save(new BookText(bookId, "header", "body"));
    }

    private static void assertOnlyRecentBooksDetected(DatalakeStorage datalake) throws IOException {
        Path oldBody = datalake.save(new BookText(1, "header", "body")).body();
        datalake.save(new BookText(1500, "header", "body"));
        Files.setLastModifiedTime(oldBody, FileTime.from(THRESHOLD.minusSeconds(60)));

        assertEquals(Set.of(1500), datalake.idsStoredSince(THRESHOLD));
    }
}
