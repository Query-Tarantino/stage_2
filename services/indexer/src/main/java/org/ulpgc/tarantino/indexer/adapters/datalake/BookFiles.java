package org.ulpgc.tarantino.indexer.adapters.datalake;

import org.ulpgc.tarantino.indexer.model.book.BookText;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Reads a book's header and body from the datalake, only once its final body exists (SPEC §4.6). */
public final class BookFiles {

    private BookFiles() {
    }

    public static Optional<BookText> bookText(int bookId, Path header, Path body) {
        return Files.exists(body) ? Optional.of(new BookText(bookId, content(header), content(body))) : Optional.empty();
    }

    private static String content(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
