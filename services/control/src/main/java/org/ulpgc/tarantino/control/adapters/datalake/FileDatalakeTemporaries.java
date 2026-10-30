package org.ulpgc.tarantino.control.adapters.datalake;

import org.ulpgc.tarantino.common.datalake.DatalakePaths;
import org.ulpgc.tarantino.control.ports.DatalakeTemporaries;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class FileDatalakeTemporaries implements DatalakeTemporaries {

    private final Path datalake;
    private final Duration maxAge;
    private final Clock clock;

    public FileDatalakeTemporaries(Path datalake, Duration maxAge, Clock clock) {
        this.datalake = datalake;
        this.maxAge = maxAge;
        this.clock = clock;
    }

    @Override
    public List<Path> orphans() {
        if (!Files.isDirectory(datalake)) {
            return List.of();
        }
        Instant writtenBefore = clock.instant().minus(maxAge);
        try (Stream<Path> files = Files.walk(datalake)) {
            return files.filter(DatalakePaths::isTemporary)
                    .filter(file -> lastModified(file).filter(modified -> modified.isBefore(writtenBefore)).isPresent())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(Path temporary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Empty when the temporary is gone, because the crawler that wrote it renamed it after the walk listed it. */
    private static Optional<Instant> lastModified(Path file) {
        try {
            return Optional.of(Files.getLastModifiedTime(file).toInstant());
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
