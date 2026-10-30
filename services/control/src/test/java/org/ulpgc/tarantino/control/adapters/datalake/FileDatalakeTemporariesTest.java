package org.ulpgc.tarantino.control.adapters.datalake;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.common.datalake.DatalakePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FileDatalakeTemporariesTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
    private static final Duration MAX_AGE = Duration.ofSeconds(600);

    @TempDir
    Path datalake;

    @Test
    void findsOnlyTheTemporariesWrittenLongerAgoThanTheMaxAge() throws IOException {
        DatalakePaths paths = new DatalakePaths(datalake);
        Path oldTemporary = file(DatalakePaths.temporary(paths.body(1342)), MAX_AGE.plusSeconds(1));
        file(DatalakePaths.temporary(paths.header(84)), MAX_AGE.minusSeconds(1));
        file(paths.header(1342), MAX_AGE.multipliedBy(2));
        file(paths.body(1342), MAX_AGE.multipliedBy(2));

        assertEquals(List.of(oldTemporary), temporaries().orphans());
    }

    @Test
    void findsNothingBeforeTheCrawlerCreatesTheDatalake() {
        assertEquals(List.of(), new FileDatalakeTemporaries(datalake.resolve("missing"), MAX_AGE, Clock.fixed(NOW, ZoneOffset.UTC)).orphans());
    }

    @Test
    void deletesATemporaryAndIgnoresOneAlreadyGone() throws IOException {
        Path temporary = file(DatalakePaths.temporary(new DatalakePaths(datalake).body(1342)), MAX_AGE.plusSeconds(1));
        FileDatalakeTemporaries temporaries = temporaries();

        temporaries.delete(temporary);
        temporaries.delete(temporary);

        assertFalse(Files.exists(temporary));
    }

    private FileDatalakeTemporaries temporaries() {
        return new FileDatalakeTemporaries(datalake, MAX_AGE, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Path file(Path path, Duration age) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, "text");
        Files.setLastModifiedTime(path, FileTime.from(NOW.minus(age)));
        return path;
    }
}
