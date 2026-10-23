package org.ulpgc.tarantino.crawler.adapters.datalake;

import org.ulpgc.tarantino.crawler.model.book.BookText;
import org.ulpgc.tarantino.crawler.model.book.StoredPaths;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class BookFiles {

    private static final String TEMPORARY_SUFFIX = ".tmp";
    // Every layout names its files <prefix>header.txt and <prefix>body.txt (§6)
    private static final String HEADER_NAME = "header.txt";
    private static final String BODY_NAME = "body.txt";

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

    public static List<Path> children(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(directory)) {
            return children.toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static boolean modifiedSince(Path file, Instant instant) {
        try {
            return Files.exists(file) && !Files.getLastModifiedTime(file).toInstant().isBefore(instant);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static int bookId(Path file, String suffix) {
        String name = file.getFileName().toString();
        return Integer.parseInt(name.substring(0, name.length() - suffix.length()));
    }

    public static int removeIncompleteWrites(Path root) {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        List<Path> incomplete = entries(root).stream().filter(Files::isRegularFile).filter(BookFiles::isIncomplete).toList();
        incomplete.forEach(BookFiles::delete);
        entries(root).stream()
                .filter(entry -> !entry.equals(root) && Files.isDirectory(entry))
                .sorted(Comparator.reverseOrder())
                .filter(BookFiles::isEmptyDirectory)
                .forEach(BookFiles::delete);
        return incomplete.size();
    }

    private static boolean isIncomplete(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(TEMPORARY_SUFFIX)
                || (name.endsWith(HEADER_NAME) && !Files.exists(file.resolveSibling(name.replace(HEADER_NAME, BODY_NAME))));
    }

    private static boolean isEmptyDirectory(Path directory) {
        try (Stream<Path> children = Files.list(directory)) {
            return children.findAny().isEmpty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> entries(Path root) {
        try (Stream<Path> entries = Files.walk(root)) {
            return entries.toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void delete(Path entry) {
        try {
            Files.delete(entry);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + TEMPORARY_SUFFIX);
        Files.createDirectories(target.getParent());
        Files.writeString(temporary, content);
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
