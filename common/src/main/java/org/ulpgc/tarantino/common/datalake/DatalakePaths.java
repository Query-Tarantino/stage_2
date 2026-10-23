package org.ulpgc.tarantino.common.datalake;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** The Stage 1 {@code batch} layout (SPEC §4.6): each book in the directory of its thousand, {@code book_id / 1000}. */
public final class DatalakePaths {

    private static final int BATCH_SIZE = 1000;
    private static final String HEADER_SUFFIX = ".header.txt";
    private static final String BODY_SUFFIX = ".body.txt";
    private static final String TEMPORARY_SUFFIX = ".tmp";

    private final Path root;

    public DatalakePaths(Path root) {
        this.root = root;
    }

    public Path header(int bookId) {
        return directory(bookId).resolve(bookId + HEADER_SUFFIX);
    }

    public Path body(int bookId) {
        return directory(bookId).resolve(bookId + BODY_SUFFIX);
    }

    /** {@code <final name>.<uuid>.tmp} in the same directory, so two writers of the same file never share it. */
    public static Path temporary(Path target) {
        return target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + TEMPORARY_SUFFIX);
    }

    /** Writes the content to a new temporary file and renames it over the final one with {@code ATOMIC_MOVE}. */
    public static void writeAtomically(Path target, String content) throws IOException {
        Path temporary = temporary(target);
        Files.createDirectories(target.getParent());
        Files.writeString(temporary, content);
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
    }

    private Path directory(int bookId) {
        return root.resolve(String.valueOf(bookId / BATCH_SIZE));
    }
}
