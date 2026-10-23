package org.ulpgc.tarantino.control.commands;

import org.ulpgc.tarantino.control.model.NextStep;
import org.ulpgc.tarantino.control.model.Outcome;
import org.ulpgc.tarantino.control.model.StepReport;
import org.ulpgc.tarantino.control.ports.ControlStateStore;
import org.ulpgc.tarantino.control.ports.Crawler;
import org.ulpgc.tarantino.control.ports.Indexer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Reads the state once and keeps it in memory, so each step costs the same however many books are done.
 * Up to {@code parallelDownloads} books are downloaded at once, started in candidates order; whichever finishes
 * first is stored, marked as downloaded and queued for indexing by the pipeline's own thread, the only one that
 * uses the datalake, the state and the index. Books are indexed in batches, one index flush per batch, and marked
 * as indexed only after it, while the next downloads go on (SPEC §9).
 */
public class ControlPipeline {

    private static final Outcome MISSING_OUTCOME = Outcome.failure("no outcome from the indexer");

    private final ControlStateStore state;
    private final Crawler crawler;
    private final Indexer indexer;
    private final List<Integer> candidates;
    private final int parallelDownloads;
    private final int indexBatch;
    private final Set<Integer> downloaded;
    private final Set<Integer> started = new HashSet<>();
    private final Deque<Integer> toIndex = new ArrayDeque<>();
    private final BlockingQueue<FinishedDownload> finished = new LinkedBlockingQueue<>();
    private int running;
    private int nextCandidate;

    public ControlPipeline(ControlStateStore state, Crawler crawler, Indexer indexer, List<Integer> candidates,
                           int parallelDownloads, int indexBatch) {
        this.state = state;
        this.crawler = crawler;
        this.indexer = indexer;
        this.candidates = candidates;
        this.parallelDownloads = parallelDownloads;
        this.indexBatch = indexBatch;
        this.downloaded = new LinkedHashSet<>(state.downloaded());
        Set<Integer> indexed = state.indexed();
        downloaded.stream().filter(id -> !indexed.contains(id)).forEach(toIndex::addLast);
    }

    public StepReport runStep() {
        startDownloads();
        if (toIndex.size() >= indexBatch || (!toIndex.isEmpty() && running == 0)) {
            List<Integer> batch = toIndex.stream().limit(indexBatch).toList();
            return new StepReport(NextStep.index(batch), indexed(batch, indexer.index(batch)));
        }
        if (running > 0) {
            FinishedDownload download = nextFinishedDownload();
            return new StepReport(NextStep.download(download.bookId()), downloaded(download));
        }
        return new StepReport(NextStep.idle(), Outcome.success("nothing left to do"));
    }

    private void startDownloads() {
        while (running < parallelDownloads) {
            Optional<Integer> candidate = pendingCandidate();
            if (candidate.isEmpty()) {
                return;
            }
            start(candidate.get());
        }
    }

    private void start(int bookId) {
        CompletableFuture<Crawler.Download> download = crawler.ingest(bookId);
        started.add(bookId);
        running++;
        download.whenComplete((book, error) -> finished.add(new FinishedDownload(bookId, book, error)));
    }

    private Optional<Integer> pendingCandidate() {
        while (nextCandidate < candidates.size() && isDone(candidates.get(nextCandidate))) {
            nextCandidate++;
        }
        return nextCandidate < candidates.size() ? Optional.of(candidates.get(nextCandidate)) : Optional.empty();
    }

    /** Downloaded in this run or a previous one, or started in this run, which a failed download is too. */
    private boolean isDone(int bookId) {
        return downloaded.contains(bookId) || started.contains(bookId);
    }

    private FinishedDownload nextFinishedDownload() {
        try {
            return finished.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a download", e);
        }
    }

    private Outcome downloaded(FinishedDownload download) {
        running--;
        Outcome outcome = download.store();
        if (outcome.succeeded()) {
            state.markDownloaded(download.bookId());
            downloaded.add(download.bookId());
            toIndex.addLast(download.bookId());
        }
        return outcome;
    }

    private Outcome indexed(List<Integer> bookIds, Map<Integer, Outcome> outcomes) {
        for (int bookId : bookIds) {
            toIndex.removeFirst();
            if (outcomeOf(bookId, outcomes).succeeded()) {
                state.markIndexed(bookId);
            }
        }
        return bookIds.size() == 1 ? outcomeOf(bookIds.getFirst(), outcomes) : summary(bookIds, outcomes);
    }

    private static Outcome summary(List<Integer> bookIds, Map<Integer, Outcome> outcomes) {
        long indexed = bookIds.stream().filter(bookId -> outcomeOf(bookId, outcomes).succeeded()).count();
        return indexed == bookIds.size()
                ? Outcome.success(indexed + " indexed")
                : Outcome.failure(indexed + " indexed, " + (bookIds.size() - indexed) + " skipped");
    }

    private static Outcome outcomeOf(int bookId, Map<Integer, Outcome> outcomes) {
        return outcomes.getOrDefault(bookId, MISSING_OUTCOME);
    }

    /** A download finished in another thread, stored in the pipeline's; an unexpected error stops the run. */
    private record FinishedDownload(int bookId, Crawler.Download download, Throwable error) {

        Outcome store() {
            if (error != null) {
                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                throw cause instanceof RuntimeException unchecked ? unchecked : new IllegalStateException(cause);
            }
            return download.store();
        }
    }
}
