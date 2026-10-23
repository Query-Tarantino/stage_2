package org.ulpgc.tarantino.control.adapters;

import org.ulpgc.tarantino.control.ports.ControlStateStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FileControlStateStore implements ControlStateStore {

    private static final Pattern BOOK_ID = Pattern.compile("\\d+");

    private final Path downloaded;
    private final Path indexed;

    public FileControlStateStore(Path root) {
        this.downloaded = root.resolve("downloaded_books.txt");
        this.indexed = root.resolve("indexed_books.txt");
    }

    @Override
    public Set<Integer> downloaded() {
        return ids(downloaded);
    }

    @Override
    public Set<Integer> indexed() {
        return ids(indexed);
    }

    @Override
    public void markDownloaded(int bookId) {
        append(downloaded, bookId);
    }

    @Override
    public void markIndexed(int bookId) {
        append(indexed, bookId);
    }

    private static Set<Integer> ids(Path file) {
        return lines(file).stream()
                .map(String::strip)
                .filter(BOOK_ID.asMatchPredicate())
                .map(Integer::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<String> lines(Path file) {
        try {
            return Files.exists(file) ? Files.readAllLines(file) : List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void append(Path file, int bookId) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, bookId + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
