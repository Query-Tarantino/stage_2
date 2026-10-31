package org.ulpgc.tarantino.bench.ingest;

import org.ulpgc.tarantino.bench.coordination.TransitionLine;
import org.ulpgc.tarantino.common.model.BookStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

/**
 * How fast a trial of an {@code ingest-throughput} run downloaded its books (SPEC §9), from its transition lines
 * (SPEC §4.8). The whole window goes from the first download claim to the last book downloaded; the steady one from
 * the download that completes the first 10 % of the books to the one that completes 90 %, so it leaves out the start
 * of the replicas and the tail where workers run out of books.
 */
public record IngestThroughput(Window whole, Window steady) {

    static final int STEADY_FROM_PERCENT = 10;
    static final int STEADY_TO_PERCENT = 90;

    private static final int PERCENT = 100;
    private static final double NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);
    private static final String QUEUED = BookStatus.QUEUED.name();
    private static final String DOWNLOADING = BookStatus.DOWNLOADING.name();
    private static final String DOWNLOADED = BookStatus.DOWNLOADED.name();

    /** The books downloaded in a window and how long it lasted. */
    public record Window(int books, double seconds) {

        public double booksPerSecond() {
            return books / seconds;
        }
    }

    /** The throughput of a trial; empty when its lines have no download claim or either window lasts no time. */
    public static Optional<IngestThroughput> of(List<TransitionLine> lines) {
        Optional<Instant> firstClaim = lines.stream().filter(line -> line.isBook() && line.moves(QUEUED, DOWNLOADING))
                .map(TransitionLine::at).min(Comparator.naturalOrder());
        List<Instant> downloads = downloadsInOrder(lines);
        if (firstClaim.isEmpty() || downloads.isEmpty()) {
            return Optional.empty();
        }
        int from = rank(STEADY_FROM_PERCENT, downloads.size());
        int to = rank(STEADY_TO_PERCENT, downloads.size());
        Window whole = window(firstClaim.get(), downloads.getLast(), downloads.size());
        Window steady = window(downloads.get(from - 1), downloads.get(to - 1), to - from);
        return whole.seconds() > 0 && steady.seconds() > 0 ? Optional.of(new IngestThroughput(whole, steady)) : Optional.empty();
    }

    private static List<Instant> downloadsInOrder(List<TransitionLine> lines) {
        return lines.stream().filter(line -> line.isBook() && line.moves(DOWNLOADING, DOWNLOADED))
                .collect(Collectors.toMap(TransitionLine::id, TransitionLine::at, BinaryOperator.minBy(Comparator.naturalOrder())))
                .values().stream().sorted().toList();
    }

    private static int rank(int percent, int books) {
        return Math.max(1, Math.ceilDiv(books * percent, PERCENT));
    }

    private static Window window(Instant start, Instant end, int books) {
        return new Window(books, Duration.between(start, end).toNanos() / NANOS_PER_SECOND);
    }
}
