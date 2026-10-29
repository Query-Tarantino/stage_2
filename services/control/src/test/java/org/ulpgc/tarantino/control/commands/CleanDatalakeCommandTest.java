package org.ulpgc.tarantino.control.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.common.datalake.DatalakePaths;
import org.ulpgc.tarantino.control.adapters.datalake.FileDatalakeTemporaries;
import org.ulpgc.tarantino.control.ports.DatalakeTemporaries;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CleanDatalakeCommandTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
    private static final Duration MAX_AGE = Duration.ofSeconds(600);

    @TempDir
    Path datalake;

    @Test
    void deletesAnOldTemporaryAndKeepsANewOneAndEveryHeaderAndBody() throws IOException {
        DatalakePaths paths = new DatalakePaths(datalake);
        Path newTemporary = file(DatalakePaths.temporary(paths.body(84)), MAX_AGE.minusSeconds(1));
        Path header = file(paths.header(1342), MAX_AGE.multipliedBy(2));
        Path body = file(paths.body(1342), MAX_AGE.multipliedBy(2));
        file(DatalakePaths.temporary(paths.body(1342)), MAX_AGE.plusSeconds(1));

        new CleanDatalakeCommand(new FileDatalakeTemporaries(datalake, MAX_AGE, Clock.fixed(NOW, ZoneOffset.UTC))).execute();

        try (Stream<Path> files = Files.walk(datalake)) {
            assertEquals(Stream.of(header, body, newTemporary).sorted().toList(),
                    files.filter(Files::isRegularFile).sorted().toList());
        }
    }

    @Test
    void survivesADatalakeItCannotRead() {
        DatalakeTemporaries unreadable = new DatalakeTemporaries() {
            @Override
            public List<Path> orphans() {
                throw new UncheckedIOException(new IOException("Input/output error"));
            }

            @Override
            public void delete(Path temporary) {
            }
        };

        assertDoesNotThrow(new CleanDatalakeCommand(unreadable)::execute);
    }

    private static Path file(Path path, Duration age) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, "text");
        Files.setLastModifiedTime(path, FileTime.from(NOW.minus(age)));
        return path;
    }
}
