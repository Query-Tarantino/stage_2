package org.ulpgc.tarantino.control.commands;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.control.model.NextStep;
import org.ulpgc.tarantino.control.model.Outcome;
import org.ulpgc.tarantino.control.model.StepReport;
import org.ulpgc.tarantino.control.ports.ControlStateStore;
import org.ulpgc.tarantino.control.ports.Crawler;
import org.ulpgc.tarantino.control.ports.Indexer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlPipelineTest {

    private static final int MISSING_BOOK = 404;
    private static final int ONE_AT_A_TIME = 1;
    private static final int BOOK_BY_BOOK = 1;
    private static final int DEFAULT_BATCH = 100;

    private final InMemoryState state = new InMemoryState();
    private final RecordingCrawler crawler = new RecordingCrawler();
    private final RecordingIndexer indexer = new RecordingIndexer();

    @Test
    void downloadingOneAtATimeDownloadsThenIndexesEachCandidateAndSkipsFailures() {
        ControlPipeline pipeline = pipeline(List.of(1, MISSING_BOOK, 2), ONE_AT_A_TIME, BOOK_BY_BOOK);

        assertEquals(List.of(NextStep.download(1), NextStep.index(List.of(1)), NextStep.download(MISSING_BOOK),
                NextStep.download(2), NextStep.index(List.of(2))), steps(pipeline));
        assertEquals(Set.of(1, 2), state.indexed());
    }

    @Test
    void indexesInBatchesAndTheLastPartialBatchWhenNothingIsLeftToDownload() {
        ControlPipeline pipeline = pipeline(List.of(1, 2, 3), ONE_AT_A_TIME, 2);

        assertEquals(List.of(NextStep.download(1), NextStep.download(2), NextStep.index(List.of(1, 2)),
                NextStep.download(3), NextStep.index(List.of(3))), steps(pipeline));
        assertEquals(List.of(List.of(1, 2), List.of(3)), indexer.batches);
        assertEquals(Set.of(1, 2, 3), state.indexed());
    }

    @Test
    void downloadsUpToTheParallelDownloadsAtOnceStartedInCandidatesOrder() {
        ControlPipeline pipeline = pipeline(List.of(1, 2, 3, 4, 5), 3, DEFAULT_BATCH);

        pipeline.runStep();
        assertEquals(List.of(1, 2, 3), crawler.started);

        steps(pipeline);
        assertEquals(List.of(1, 2, 3, 4, 5), crawler.started);
        assertEquals(3, crawler.mostRunning);
        assertEquals(Set.of(1, 2, 3, 4, 5), state.indexed());
    }

    @Test
    void storesMarksAndQueuesBooksInTheOrderTheirDownloadsFinish() {
        CompletableFuture<Crawler.Download> slowDownload = new CompletableFuture<>();
        Crawler firstFinishesLast = bookId -> bookId == 1 ? slowDownload : CompletableFuture.completedFuture(() -> {
            slowDownload.complete(() -> Outcome.success("stored"));
            return Outcome.success("stored");
        });

        assertEquals(List.of(NextStep.download(2), NextStep.download(1), NextStep.index(List.of(2, 1))),
                steps(new ControlPipeline(state, firstFinishesLast, indexer, List.of(1, 2), 2, 2)));
        assertEquals(List.of(2, 1), List.copyOf(state.downloaded()));
    }

    @Test
    void startsTheNextDownloadsBeforeIndexingABatch() {
        List<List<Integer>> startedWhenIndexing = new ArrayList<>();
        Indexer watched = bookIds -> {
            startedWhenIndexing.add(List.copyOf(crawler.started));
            return indexer.index(bookIds);
        };

        steps(new ControlPipeline(state, crawler, watched, List.of(1, 2, 3), 2, BOOK_BY_BOOK));

        assertEquals(List.of(1, 2, 3), startedWhenIndexing.getFirst());
    }

    @Test
    void stopsWhenADownloadFailsUnexpectedly() {
        Crawler broken = bookId -> CompletableFuture.failedFuture(new IllegalStateException("bug in the crawler"));
        ControlPipeline pipeline = new ControlPipeline(state, broken, indexer, List.of(1), ONE_AT_A_TIME, BOOK_BY_BOOK);

        assertThrows(IllegalStateException.class, pipeline::runStep);
        assertEquals(Set.of(), state.downloaded);
    }

    @Test
    void resumesByIndexingBooksDownloadedBeforeAnInterruption() {
        state.markDownloaded(7);

        assertEquals(NextStep.index(List.of(7)), pipeline(List.of(), ONE_AT_A_TIME, DEFAULT_BATCH).runStep().step());
    }

    @Test
    void marksTheBooksOfABatchThatSucceedAndGoesOnWithoutTheOnesThatFail() {
        state.markDownloaded(MISSING_BOOK);
        state.markDownloaded(8);
        ControlPipeline pipeline = pipeline(List.of(), ONE_AT_A_TIME, DEFAULT_BATCH);

        StepReport report = pipeline.runStep();

        assertEquals(Outcome.failure("1 indexed, 1 skipped"), report.outcome());
        assertEquals(Set.of(8), state.indexed());
        assertTrue(pipeline.runStep().idle());
    }

    @Test
    void marksNoBookOfABatchInterruptedBeforeItsFlush() {
        state.markDownloaded(1);
        state.markDownloaded(2);
        Indexer interrupted = bookIds -> {
            throw new IllegalStateException("interrupted while flushing");
        };
        ControlPipeline pipeline = new ControlPipeline(state, crawler, interrupted, List.of(), ONE_AT_A_TIME, DEFAULT_BATCH);

        assertThrows(IllegalStateException.class, pipeline::runStep);
        assertEquals(Set.of(), state.indexed());
    }

    @Test
    void skipsCandidatesAlreadyDownloadedOrRepeated() {
        state.markDownloaded(1);
        state.markIndexed(1);

        assertEquals(List.of(NextStep.download(2), NextStep.index(List.of(2))),
                steps(pipeline(List.of(1, 2, 1, 2), 2, BOOK_BY_BOOK)));
        assertEquals(List.of(2), crawler.started);
    }

    @Test
    void readsTheStateOnceWhateverTheNumberOfSteps() {
        steps(pipeline(List.of(1, 2, 3, 4, 5), ONE_AT_A_TIME, BOOK_BY_BOOK));

        assertEquals(1, state.reads);
    }

    private ControlPipeline pipeline(List<Integer> candidates, int parallelDownloads, int indexBatch) {
        return new ControlPipeline(state, crawler, indexer, candidates, parallelDownloads, indexBatch);
    }

    private static List<NextStep> steps(ControlPipeline pipeline) {
        return Stream.generate(pipeline::runStep).takeWhile(report -> !report.idle()).map(StepReport::step).toList();
    }

    /** Downloads that finish at once, in the order they start; a download runs until the pipeline stores it. */
    private static class RecordingCrawler implements Crawler {

        private final List<Integer> started = new ArrayList<>();
        private int stored;
        private int mostRunning;

        @Override
        public CompletableFuture<Download> ingest(int bookId) {
            started.add(bookId);
            mostRunning = Math.max(mostRunning, started.size() - stored);
            return CompletableFuture.completedFuture(() -> {
                stored++;
                return bookId == MISSING_BOOK ? Outcome.failure("not found") : Outcome.success("stored");
            });
        }
    }

    private static class RecordingIndexer implements Indexer {

        private final List<List<Integer>> batches = new ArrayList<>();

        @Override
        public Map<Integer, Outcome> index(List<Integer> bookIds) {
            batches.add(bookIds);
            Map<Integer, Outcome> outcomes = new LinkedHashMap<>();
            bookIds.forEach(bookId -> outcomes.put(bookId, bookId == MISSING_BOOK ? Outcome.failure("not found") : Outcome.success("indexed")));
            return outcomes;
        }
    }

    private static class InMemoryState implements ControlStateStore {

        private final Set<Integer> downloaded = new LinkedHashSet<>();
        private final Set<Integer> indexed = new LinkedHashSet<>();
        private int reads;

        @Override
        public Set<Integer> downloaded() {
            reads++;
            return new LinkedHashSet<>(downloaded);
        }

        @Override
        public Set<Integer> indexed() {
            return new LinkedHashSet<>(indexed);
        }

        @Override
        public void markDownloaded(int bookId) {
            downloaded.add(bookId);
        }

        @Override
        public void markIndexed(int bookId) {
            indexed.add(bookId);
        }
    }
}
