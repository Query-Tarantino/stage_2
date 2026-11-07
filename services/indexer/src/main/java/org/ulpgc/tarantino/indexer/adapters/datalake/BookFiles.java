package org.ulpgc.tarantino.indexer.adapters.datalake;

import org.ulpgc.tarantino.indexer.model.book.BookText;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads a book's header and body from the datalake, only once both final files exist (SPEC §4.6). A missing header is
 * treated like a missing body, so the book fails with {@code NOT_IN_DATALAKE} (SPEC §4.5) instead of an exception that
 * would drop its whole batch every time it is claimed again.
 */
public final class BookFiles {

    private BookFiles() {
    }

    public static Optional<BookText> bookText(int bookId, Path header, Path body) {
        if (!Files.exists(header) || !Files.exists(body)) {
            return Optional.empty();
        }
        return Optional.of(new BookText(bookId, content(header), content(body)));
    }

    private static String content(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
