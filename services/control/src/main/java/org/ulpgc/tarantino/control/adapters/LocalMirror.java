package org.ulpgc.tarantino.control.adapters;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class LocalMirror {

    private static final Pattern BOOK_ID = Pattern.compile("[1-9][0-9]{0,8}");

    private final Path mirror;

    public LocalMirror(Path mirror) {
        this.mirror = mirror;
    }

    /** The books of the mirror in ascending id order: the directories named by a book id that hold its text. */
    public List<Integer> bookIds() {
        try (Stream<Path> entries = Files.list(mirror)) {
            return entries.map(entry -> entry.getFileName().toString())
                    .filter(BOOK_ID.asMatchPredicate())
                    .map(Integer::valueOf)
                    .filter(bookId -> Files.isRegularFile(text(bookId)))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path text(int bookId) {
        return mirror.resolve(String.valueOf(bookId)).resolve("pg" + bookId + ".txt");
    }
}
