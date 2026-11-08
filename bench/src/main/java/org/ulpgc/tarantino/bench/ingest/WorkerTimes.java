package org.ulpgc.tarantino.bench.ingest;

import org.ulpgc.tarantino.bench.coordination.TransitionLine;
import org.ulpgc.tarantino.common.model.BookStatus;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * How the download workers of an {@code ingest-throughput} trial spend their time (SPEC §9), from the transition lines
 * of each worker (SPEC §4.8). A book's work goes from its download claim to its download by the same worker: reading
 * it, splitting it, writing it to the datalake and “Finish a download”. A gap goes from a download to that worker's
 * next claim: the enqueue in {@code books-to-index}, the poll of {@code books-to-download} and “Claim a book”.
 */
public record WorkerTimes(List<Double> workMillis, List<Double> gapMillis) {

    private static final double NANOS_PER_MILLI = 1_000_000;
    private static final String QUEUED = BookStatus.QUEUED.name();
    private static final String DOWNLOADING = BookStatus.DOWNLOADING.name();
    private static final String DOWNLOADED = BookStatus.DOWNLOADED.name();

    /** The times of a trial's workers; empty when no worker downloaded a book it had claimed. */
    static Optional<WorkerTimes> of(List<WorkerLine> lines) {
        Collection<List<TransitionLine>> workers = lines.stream()
                .filter(line -> line.transition().isBook())
                .collect(Collectors.groupingBy(WorkerLine::worker, LinkedHashMap::new,
                        Collectors.mapping(WorkerLine::transition, Collectors.toList())))
                .values();
        List<Double> work = workers.stream().flatMap(worker -> work(inOrder(worker))).toList();
        List<Double> gaps = workers.stream().flatMap(worker -> gaps(inOrder(worker))).toList();
        return work.isEmpty() ? Optional.empty() : Optional.of(new WorkerTimes(work, gaps));
    }

    private static List<TransitionLine> inOrder(List<TransitionLine> worker) {
        return worker.stream().sorted(Comparator.comparing(TransitionLine::at)).toList();
    }

    private static Stream<Double> work(List<TransitionLine> inOrder) {
        return consecutive(inOrder)
                .filter(pair -> pair.first().moves(QUEUED, DOWNLOADING) && pair.second().moves(DOWNLOADING, DOWNLOADED)
                        && pair.first().id().equals(pair.second().id()))
                .map(Pair::millis);
    }

    private static Stream<Double> gaps(List<TransitionLine> inOrder) {
        return consecutive(inOrder)
                .filter(pair -> pair.first().moves(DOWNLOADING, DOWNLOADED) && pair.second().moves(QUEUED, DOWNLOADING))
                .map(Pair::millis);
    }

    private static Stream<Pair> consecutive(List<TransitionLine> inOrder) {
        return IntStream.range(1, inOrder.size()).mapToObj(index -> new Pair(inOrder.get(index - 1), inOrder.get(index)));
    }

    private record Pair(TransitionLine first, TransitionLine second) {

        double millis() {
            return Duration.between(first.at(), second.at()).toNanos() / NANOS_PER_MILLI;
        }
    }
}
