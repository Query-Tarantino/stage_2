package org.ulpgc.tarantino.control.adapters.datalake;

import org.ulpgc.tarantino.control.ports.DatalakeTemporaries;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/** The {@code .tmp} files of the datalake older than {@code TARANTINO_TMP_MAX_AGE_SECONDS}, which only {@code control} deletes (SPEC §4.6). */
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
        OrphanTemporaries orphans = new OrphanTemporaries(clock.instant().minus(maxAge));
        try {
            Files.walkFileTree(datalake, orphans);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return orphans.found();
    }

    @Override
    public void delete(Path temporary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
