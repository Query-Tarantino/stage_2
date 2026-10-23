package org.ulpgc.tarantino.indexer.commands;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.model.BatchPublished;
import org.ulpgc.tarantino.common.operations.TransitionLog;
import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.indexer.model.batch.Batch;
import org.ulpgc.tarantino.indexer.model.book.BookMetadata;
import org.ulpgc.tarantino.indexer.model.book.BookText;
import org.ulpgc.tarantino.indexer.model.book.HeaderParser;
import org.ulpgc.tarantino.indexer.model.terms.TermPostings;
import org.ulpgc.tarantino.indexer.ports.BatchStore;
import org.ulpgc.tarantino.indexer.ports.IndexWriter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndexBatchCommandTest {

    private static final Instant ENQUEUED_AT = Instant.parse("2026-10-21T10:05:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-10-21T10:05:32Z");
    private static final String BATCH_ID = "6716f0c2a1b2c3d4e5f60718";

    private final FakeBatchStore batches = new FakeBatchStore();
    private final RecordingWriter writer = new RecordingWriter();
    private final List<BatchPublished> announced = new ArrayList<>();
    private final Map<Integer, BookText> datalake = new HashMap<>();

    @Test
    void publishesTheClaimedBooksWithTheirMetadataAndPostings() {
        store(1342, "Title: Pride and Prejudice", "pride and prejudice, pride");
        store(84, "Title: Frankenstein", "pride");

        command().execute(books(1342, 84));

        assertEquals(List.of(84, 1342), writer.metadata.stream().map(BookMetadata::bookId).toList());
        assertEquals(List.of(new TermPostings("prejudice", List.of(1342), List.of(1)),
                new TermPostings("pride", List.of(84, 1342), List.of(1, 2))), writer.postings);
        assertEquals(Map.of(BATCH_ID, List.of(84, 1342)), batches.published);
        assertEquals(Set.of(84, 1342), batches.marked);
        assertEquals(List.of(new BatchPublished(BATCH_ID, 1, 2, PUBLISHED_AT)), announced);
    }

    @Test
    void leavesOutTheBooksItCouldNotClaim() {
        store(1342, "Title: Pride and Prejudice", "pride");
        store(84, "Title: Frankenstein", "monster");
        batches.downloaded.remove(84);

        command().execute(books(1342, 84));

        assertEquals(Map.of(BATCH_ID, List.of(1342)), batches.published);
    }

    @Test
    void failsABookMissingFromTheDatalakeAndPublishesTheRest() {
        store(1342, "Title: Pride and Prejudice", "pride");
        batches.downloaded.add(84);

        command().execute(books(1342, 84));

        assertEquals(List.of(84), batches.failed);
        assertEquals(Map.of(BATCH_ID, List.of(1342)), batches.published);
    }

    @Test
    void neverPublishesABatchLeftWithoutBooks() {
        batches.downloaded.add(84);

        command().execute(books(84));

        assertEquals(List.of(), writer.metadata);
        assertEquals(Map.of(), batches.published);
        assertEquals(List.of(), announced);
    }

    @Test
    void neitherMarksNorAnnouncesABatchThatLostItsLease() {
        store(1342, "Title: Pride and Prejudice", "pride");
        batches.leaseLost = true;

        command().execute(books(1342));

        assertEquals(Set.of(), batches.marked);
        assertEquals(List.of(), announced);
    }

    private void store(int bookId, String header, String body) {
        datalake.put(bookId, new BookText(bookId, header, body));
        batches.downloaded.add(bookId);
    }

    private static Map<Integer, Instant> books(Integer... bookIds) {
        Map<Integer, Instant> books = new HashMap<>();
        List.of(bookIds).forEach(bookId -> books.put(bookId, ENQUEUED_AT));
        return books;
    }

    private IndexBatchCommand command() {
        Tokenizer tokenizer = new Tokenizer(Set.of("and"));
        return new IndexBatchCommand(batches, bookId -> Optional.ofNullable(datalake.get(bookId)), new HeaderParser(tokenizer),
                tokenizer, writer, announced::add, new TransitionLog());
    }

    private static final class FakeBatchStore implements BatchStore {

        private final Set<Integer> downloaded = new HashSet<>();
        private final Set<Integer> indexing = new HashSet<>();
        private final List<Integer> failed = new ArrayList<>();
        private final Map<String, List<Integer>> published = new HashMap<>();
        private final Set<Integer> marked = new HashSet<>();
        private boolean leaseLost;

        @Override
        public int writeGeneration() {
            return 1;
        }

        @Override
        public Batch open(int generation) {
            return new Batch(BATCH_ID, "claim", generation);
        }

        @Override
        public List<Integer> claim(Batch batch, Collection<Integer> bookIds) {
            List<Integer> claimed = bookIds.stream().filter(downloaded::contains).sorted().toList();
            downloaded.removeAll(claimed);
            indexing.addAll(claimed);
            return claimed;
        }

        @Override
        public boolean failAtIndexing(Batch batch, int bookId) {
            failed.add(bookId);
            return indexing.remove(bookId);
        }

        @Override
        public Optional<Instant> publish(Batch batch, List<Integer> bookIds) {
            if (leaseLost) {
                return Optional.empty();
            }
            published.put(batch.batchId(), bookIds);
            return Optional.of(PUBLISHED_AT);
        }

        @Override
        public void markPublished(Batch batch) {
            marked.addAll(indexing);
        }
    }

    private static final class RecordingWriter implements IndexWriter {

        private final List<BookMetadata> metadata = new ArrayList<>();
        private final List<TermPostings> postings = new ArrayList<>();

        @Override
        public void writeMetadata(int generation, List<BookMetadata> books) {
            metadata.addAll(books);
        }

        @Override
        public void writePostings(Batch batch, List<TermPostings> batchPostings) {
            postings.addAll(batchPostings);
        }
    }
}
