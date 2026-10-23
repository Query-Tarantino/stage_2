package org.ulpgc.tarantino.crawler.adapters.gutenberg;

import org.ulpgc.tarantino.common.model.BookErrorCode;
import org.ulpgc.tarantino.crawler.model.failure.DownloadException;
import org.ulpgc.tarantino.crawler.ports.BookDownloader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads books from a local copy of Project Gutenberg's generated collection, made in bulk with rsync
 * (SPEC §4.7), instead of one HTTP request per book. Same files, same failures (§4.5): a missing book is
 * NOT_FOUND and any other I/O error NETWORK_ERROR.
 */
public class LocalMirrorDownloader implements BookDownloader {

    private final Path mirror;

    public LocalMirrorDownloader(Path mirror) {
        this.mirror = mirror;
    }

    @Override
    public String rawText(int bookId) throws DownloadException {
        Path file = file(bookId);
        if (!Files.isRegularFile(file)) {
            throw new DownloadException(BookErrorCode.NOT_FOUND, "Book " + bookId + " not found in the mirror " + mirror);
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DownloadException(BookErrorCode.NETWORK_ERROR, "Could not read book " + bookId + " from the mirror: " + e);
        }
    }

    private Path file(int bookId) {
        return mirror.resolve(String.valueOf(bookId)).resolve("pg" + bookId + ".txt");
    }
}
