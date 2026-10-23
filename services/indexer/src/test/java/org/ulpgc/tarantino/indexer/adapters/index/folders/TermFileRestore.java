package org.ulpgc.tarantino.indexer.adapters.index.folders;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Puts back the term files of some terms as they are in a snapshot, deleting the ones it does not have. Files are
 * restored in parallel, and each folder is created once, before the first file copied into it.
 */
public final class TermFileRestore {

    private TermFileRestore() {
    }

    public static void restore(Path snapshot, Path index, Collection<String> terms) {
        Map<Path, Path> folders = new ConcurrentHashMap<>();
        terms.parallelStream().forEach(term -> restore(TermFiles.file(snapshot, term), TermFiles.file(index, term), folders));
    }

    private static void restore(Path stored, Path current, Map<Path, Path> folders) {
        try {
            if (Files.exists(stored)) {
                folders.computeIfAbsent(current.getParent(), TermFileRestore::createDirectories);
                Files.copy(stored, current, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(current);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path createDirectories(Path folder) {
        try {
            return Files.createDirectories(folder);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
