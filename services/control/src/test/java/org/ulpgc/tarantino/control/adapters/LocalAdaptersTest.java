package org.ulpgc.tarantino.control.adapters;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.control.model.Outcome;
import org.ulpgc.tarantino.control.ports.Crawler;
import org.ulpgc.tarantino.crawler.commands.IngestBookCommand;
import org.ulpgc.tarantino.crawler.model.book.StoredPaths;
import org.ulpgc.tarantino.crawler.model.failure.DownloadException;
import org.ulpgc.tarantino.crawler.model.failure.FailureReason;
import org.ulpgc.tarantino.crawler.ports.BookDownloader;
import org.ulpgc.tarantino.crawler.ports.DatalakeStorage;
import org.ulpgc.tarantino.indexer.commands.IndexBookCommand;
import org.ulpgc.tarantino.indexer.model.book.BookText;
import org.ulpgc.tarantino.indexer.model.book.HeaderParser;
import org.ulpgc.tarantino.indexer.model.terms.TermOccurrences;
import org.ulpgc.tarantino.indexer.model.terms.Tokenizer;
import org.ulpgc.tarantino.indexer.ports.datamarts.InvertedIndexStorage;
import org.ulpgc.tarantino.indexer.ports.sources.DatalakeReader;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LocalAdaptersTest {

    private static final String RAW = "Title: T\n*** START OF THE PROJECT GUTENBERG EBOOK T ***\nbody\n*** END OF THE PROJECT GUTENBERG EBOOK T ***";
    private static final BookDownloader UNREACHABLE = bookId -> {
        throw new AssertionError("Book " + bookId + " must not be downloaded");
    };

    private final RecordingDatalake datalake = new RecordingDatalake();

    @Test
    void crawlerReportsWhereABookAlreadyStoredIsWithoutDownloadingIt() {
        datalake.save(new org.ulpgc.tarantino.crawler.model.book.BookText(5, "header", "body"));

        assertEquals(Outcome.success("stored in datalake/5"), new LocalCrawler(new IngestBookCommand(UNREACHABLE, datalake), Runnable::run)
                .ingest(5).join().store());
    }

    @Test
    void crawlerDownloadsWithItsExecutorAndWritesTheDatalakeOnlyWhenStoring() {
        List<Runnable> downloads = new ArrayList<>();
        LocalCrawler crawler = new LocalCrawler(new IngestBookCommand(bookId -> RAW, datalake), downloads::add);

        CompletableFuture<Crawler.Download> download = crawler.ingest(5);
        assertFalse(download.isDone());
        downloads.forEach(Runnable::run);
        assertEquals(List.of(), datalake.saved);

        assertEquals(Outcome.success("stored in datalake/5"), download.join().store());
        assertEquals(List.of(5), datalake.saved);
    }

    @Test
    void crawlerReportsTheFailureReason() {
        BookDownloader missing = bookId -> {
            throw new DownloadException(FailureReason.NOT_FOUND, "missing");
        };

        assertEquals(Outcome.failure("skipped, NOT_FOUND"),
                new LocalCrawler(new IngestBookCommand(missing, datalake), Runnable::run).ingest(5).join().store());
        assertEquals(List.of(), datalake.saved);
    }

    @Test
    void indexerReportsTheUniqueTermsOfEachBookAndTheOnesMissingFromTheDatalake() {
        BookText text = new BookText(5, "Title: T", "island whale island", Path.of("5.body.txt"));
        LocalIndexer indexer = new LocalIndexer(index(bookId -> Optional.of(text).filter(found -> bookId == 5)));

        assertEquals(Map.of(5, Outcome.success("2 unique terms indexed"), 6, Outcome.failure("skipped, not found in the datalake")),
                indexer.index(List.of(5, 6)));
    }

    private static IndexBookCommand index(DatalakeReader datalake) {
        InvertedIndexStorage index = new InvertedIndexStorage() {
            @Override
            public void add(TermOccurrences occurrences) {
            }

            @Override
            public void flush() {
            }
        };
        return new IndexBookCommand(datalake, new HeaderParser(), new Tokenizer(Set.of()), index, book -> {
        });
    }

    private static class RecordingDatalake implements DatalakeStorage {

        private final Map<Integer, StoredPaths> books = new HashMap<>();
        private final List<Integer> saved = new ArrayList<>();

        @Override
        public StoredPaths save(org.ulpgc.tarantino.crawler.model.book.BookText book) {
            Path directory = Path.of("datalake", String.valueOf(book.bookId()));
            StoredPaths paths = new StoredPaths(directory.resolve("header.txt"), directory.resolve("body.txt"));
            books.put(book.bookId(), paths);
            saved.add(book.bookId());
            return paths;
        }

        @Override
        public Optional<StoredPaths> pathsOf(int bookId) {
            return Optional.ofNullable(books.get(bookId));
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
