package org.ulpgc.tarantino.crawler.commands;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.crawler.model.book.BookText;
import org.ulpgc.tarantino.crawler.model.book.StoredPaths;
import org.ulpgc.tarantino.crawler.model.failure.DownloadException;
import org.ulpgc.tarantino.crawler.model.failure.FailureReason;
import org.ulpgc.tarantino.crawler.ports.BookDownloader;
import org.ulpgc.tarantino.crawler.ports.DatalakeStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class IngestBookCommandTest {

    private static final String RAW = "Title: T\n*** START OF THE PROJECT GUTENBERG EBOOK T ***\nbody\n*** END OF THE PROJECT GUTENBERG EBOOK T ***";
    private static final BookDownloader UNREACHABLE = bookId -> {
        throw new AssertionError("Book " + bookId + " must not be downloaded");
    };

    private final InMemoryDatalake datalake = new InMemoryDatalake();

    @Test
    void downloadsSplitsAndStoresNewBooks() {
        IngestResult result = new IngestBookCommand(bookId -> RAW, datalake).execute(5);

        assertEquals(IngestResult.success(5, InMemoryDatalake.paths(5)), result);
        assertEquals(new BookText(5, "Title: T", "body"), datalake.books.get(5));
    }

    @Test
    void reusesBooksAlreadyInTheDatalakeWithoutDownloading() {
        datalake.save(new BookText(5, "header", "body"));

        assertEquals(IngestResult.success(5, InMemoryDatalake.paths(5)), new IngestBookCommand(UNREACHABLE, datalake).execute(5));
    }

    @Test
    void reportsDownloadFailuresWithoutStoring() {
        BookDownloader missing = bookId -> {
            throw new DownloadException(FailureReason.NOT_FOUND, "missing");
        };

        assertEquals(IngestResult.failure(5, FailureReason.NOT_FOUND), new IngestBookCommand(missing, datalake).execute(5));
        assertFalse(datalake.books.containsKey(5));
    }

    @Test
    void reportsBooksWithoutMarkersAsMissingMarkers() {
        assertEquals(IngestResult.failure(5, FailureReason.MISSING_MARKERS), new IngestBookCommand(bookId -> "no markers", datalake).execute(5));
    }

    @Test
    void downloadsWithoutWritingTheDatalakeUntilTheBookIsStored() {
        IngestBookCommand ingest = new IngestBookCommand(bookId -> RAW, datalake);

        DownloadResult download = ingest.download(5);
        assertEquals(DownloadResult.success(new BookText(5, "Title: T", "body")), download);
        assertFalse(datalake.books.containsKey(5));

        assertEquals(IngestResult.success(5, InMemoryDatalake.paths(5)), ingest.store(download));
        assertEquals(new BookText(5, "Title: T", "body"), datalake.books.get(5));
    }

    @Test
    void storesNothingForAFailedDownload() {
        IngestBookCommand ingest = new IngestBookCommand(UNREACHABLE, datalake);

        assertEquals(IngestResult.failure(5, FailureReason.NETWORK_ERROR), ingest.store(DownloadResult.failure(5, FailureReason.NETWORK_ERROR)));
        assertFalse(datalake.books.containsKey(5));
    }

    @Test
    void reportsStorageErrors() {
        DatalakeStorage broken = new InMemoryDatalake() {
            @Override
            public StoredPaths save(BookText book) {
                throw new UncheckedIOException(new IOException("disk full"));
            }
        };

        assertEquals(IngestResult.failure(5, FailureReason.STORAGE_ERROR), new IngestBookCommand(bookId -> RAW, broken).execute(5));
    }

    private static class InMemoryDatalake implements DatalakeStorage {

        private final Map<Integer, BookText> books = new HashMap<>();

        static StoredPaths paths(int bookId) {
            return new StoredPaths(Path.of(bookId + ".header.txt"), Path.of(bookId + ".body.txt"));
        }

        @Override
        public StoredPaths save(BookText book) {
            books.put(book.bookId(), book);
            return paths(book.bookId());
        }

        @Override
        public Optional<StoredPaths> pathsOf(int bookId) {
            return Optional.of(bookId).filter(books::containsKey).map(InMemoryDatalake::paths);
        }

        @Override
        public Set<Integer> idsStoredSince(Instant instant) {
            return books.keySet();
        }

        @Override
        public int removeIncompleteWrites() {
            return 0;
        }
    }
}
