package org.ulpgc.tarantino.crawler.adapters.gutenberg;

import org.ulpgc.tarantino.crawler.model.failure.DownloadException;
import org.ulpgc.tarantino.crawler.model.failure.FailureReason;
import org.ulpgc.tarantino.crawler.ports.BookDownloader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads books from a local copy of Project Gutenberg's generated collection, made in bulk with rsync
 * (SPEC §4), instead of one HTTP request per book. Same files, same failures: a missing book is NOT_FOUND
 * and any other I/O error NETWORK_ERROR.
 */
public class LocalMirrorDownloader implements BookDownloader {

    private static final Pattern BOOK_ID = Pattern.compile("[1-9][0-9]{0,8}");

    private final Path mirror;

    public LocalMirrorDownloader(Path mirror) {
        this.mirror = mirror;
    }

    @Override
    public String rawText(int bookId) throws DownloadException {
        Path file = file(bookId);
        if (!Files.isRegularFile(file)) {
            throw new DownloadException(FailureReason.NOT_FOUND, "Book " + bookId + " not found in the mirror " + mirror);
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DownloadException(FailureReason.NETWORK_ERROR, "Could not read book " + bookId + " from the mirror: " + e);
        }
    }

    /** The books of the mirror in ascending id order: the directories named by a book id that hold its text. */
    public List<Integer> bookIds() {
        try (Stream<Path> entries = Files.list(mirror)) {
            return entries.map(entry -> entry.getFileName().toString())
                    .filter(BOOK_ID.asMatchPredicate())
                    .map(Integer::valueOf)
                    .filter(bookId -> Files.isRegularFile(file(bookId)))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path file(int bookId) {
        return mirror.resolve(String.valueOf(bookId)).resolve("pg" + bookId + ".txt");
    }
}
