package org.ulpgc.tarantino.control.adapters.datalake;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrphanTemporariesTest {

    private static final Path RENAMED_TEMPORARY = Path.of("datalake", "13", "1342.body.txt.0f8c.tmp");

    private final OrphanTemporaries orphans = new OrphanTemporaries(Instant.parse("2026-10-21T10:00:00Z"));

    @Test
    void keepsWalkingPastATemporaryRenamedAfterItsFolderWasListed() throws IOException {
        FileVisitResult result = orphans.visitFileFailed(RENAMED_TEMPORARY, new NoSuchFileException(RENAMED_TEMPORARY.toString()));

        assertEquals(FileVisitResult.CONTINUE, result);
        assertEquals(List.of(), orphans.found());
    }

    @Test
    void stopsTheWalkOnAnyOtherFailure() {
        assertThrows(AccessDeniedException.class,
                () -> orphans.visitFileFailed(RENAMED_TEMPORARY, new AccessDeniedException(RENAMED_TEMPORARY.toString())));
    }
}
