package org.ulpgc.tarantino.control.commands;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.common.operations.Transition;
import org.ulpgc.tarantino.control.model.GenerationBatch;
import org.ulpgc.tarantino.control.model.IndexingBook;
import org.ulpgc.tarantino.control.ports.IndexingBooks;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResolveIndexingBooksCommandTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:05:00Z");
    private static final GenerationBatch WRITING = new GenerationBatch(1, "6531a0c2e4b0a1b2c3d4e5f1");
    private static final GenerationBatch ABANDONED = new GenerationBatch(1, "6531a0c2e4b0a1b2c3d4e5f2");
    private static final GenerationBatch MISSING = new GenerationBatch(2, "6531a0c2e4b0a1b2c3d4e5f3");
    private static final GenerationBatch PUBLISHED = new GenerationBatch(2, "6531a0c2e4b0a1b2c3d4e5f4");

    private final BooksInBatches books = new BooksInBatches();
    private final List<BookTask> queue = new ArrayList<>();
    private final RecordingTransitionLog log = new RecordingTransitionLog();
    private final ResolveIndexingBooksCommand resolve =
            new ResolveIndexingBooksCommand(books, queue::add, log, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void indexBooksInEveryKindOfBatch() {
        books.batch(WRITING, BatchStatus.WRITING);
        books.batch(ABANDONED, BatchStatus.ABANDONED);
        books.batch(PUBLISHED, BatchStatus.PUBLISHED);
        books.indexing(11, WRITING, true);
        books.indexing(1342, ABANDONED, true);
        books.indexing(84, ABANDONED, false);
        books.indexing(98, MISSING, true);
        books.indexing(2701, PUBLISHED, true);
        books.indexing(1513, PUBLISHED, false);
        resolve.execute();
    }

    @Test
    void enqueuesOnlyTheBooksItReleased() {
        assertEquals(List.of(new BookTask(1342, NOW), new BookTask(98, NOW)), queue);
    }

    @Test
    void logsOnlyTheWritesThatMatchedWithTheirBatch() {
        assertEquals(List.of(
                Transition.book(1342, BookStatus.INDEXING, BookStatus.DOWNLOADED).inBatch(ABANDONED.batchId(), 1),
                Transition.book(98, BookStatus.INDEXING, BookStatus.DOWNLOADED).inBatch(MISSING.batchId(), 2),
                Transition.book(2701, BookStatus.INDEXING, BookStatus.PUBLISHED).inBatch(PUBLISHED.batchId(), 2)),
                log.transitions());
    }

    @Test
    void readsTheStateOfEachBatchOnce() {
        assertEquals(List.of(WRITING, ABANDONED, MISSING, PUBLISHED), books.batchesRead());
    }

    private static final class BooksInBatches implements IndexingBooks {

        private final Map<GenerationBatch, BatchStatus> batches = new HashMap<>();
        private final Map<IndexingBook, Boolean> outcomes = new LinkedHashMap<>();
        private final List<GenerationBatch> batchesRead = new ArrayList<>();

        void batch(GenerationBatch batch, BatchStatus status) {
            batches.put(batch, status);
        }

        void indexing(int bookId, GenerationBatch batch, boolean matches) {
            outcomes.put(new IndexingBook(bookId, batch), matches);
        }

        List<GenerationBatch> batchesRead() {
            return batchesRead;
        }

        @Override
        public List<IndexingBook> indexing() {
            return List.copyOf(outcomes.keySet());
        }

        @Override
        public Optional<BatchStatus> statusOf(GenerationBatch batch) {
            batchesRead.add(batch);
            return Optional.ofNullable(batches.get(batch));
        }

        @Override
        public boolean release(IndexingBook book) {
            return outcomes.get(book);
        }

        @Override
        public boolean complete(IndexingBook book) {
            return outcomes.get(book);
        }
    }
}
