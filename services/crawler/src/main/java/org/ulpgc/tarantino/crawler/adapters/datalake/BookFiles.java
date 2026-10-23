package org.ulpgc.tarantino.crawler.adapters.datalake;

import org.ulpgc.tarantino.crawler.model.book.BookText;
import org.ulpgc.tarantino.crawler.model.book.StoredPaths;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public final class BookFiles {

    private static final String TEMPORARY_SUFFIX = ".tmp";

    private BookFiles() {
    }

    public static StoredPaths write(StoredPaths paths, BookText book) {
        try {
            writeAtomically(paths.header(), book.header());
            writeAtomically(paths.body(), book.body());
            return paths;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Optional<StoredPaths> existing(StoredPaths paths) {
        return Optional.of(paths).filter(candidate -> Files.exists(candidate.body()));
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + TEMPORARY_SUFFIX);
        Files.createDirectories(target.getParent());
        Files.writeString(temporary, content);
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
