package org.ulpgc.tarantino.control.adapters.datalake;

import org.ulpgc.tarantino.common.datalake.DatalakePaths;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects the temporaries written before an instant while walking the datalake, and skips the ones a crawler renames
 * between the walk listing a folder and reading their attributes (SPEC §4.6).
 */
class OrphanTemporaries extends SimpleFileVisitor<Path> {

    private final Instant writtenBefore;
    private final List<Path> found = new ArrayList<>();

    OrphanTemporaries(Instant writtenBefore) {
        this.writtenBefore = writtenBefore;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
        if (DatalakePaths.isTemporary(file) && attributes.lastModifiedTime().toInstant().isBefore(writtenBefore)) {
            found.add(file);
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
        if (failure instanceof NoSuchFileException) {
            return FileVisitResult.CONTINUE;
        }
        throw failure;
    }

    List<Path> found() {
        return List.copyOf(found);
    }
}
