package org.ulpgc.tarantino.bench.resources;

import org.ulpgc.tarantino.bench.coordination.TransitionLine;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.model.BookStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * When a phase of a trial ran, from its transition lines (SPEC §4.8): ingestion from the first download claim to the
 * last book downloaded, indexing from the first index claim to the last batch published.
 */
public record PhaseWindow(String phase, Instant start, Instant end) {

    public static final String INGESTION = "ingestion";
    public static final String INDEXING = "indexing";

    private static final String QUEUED = BookStatus.QUEUED.name();
    private static final String DOWNLOADING = BookStatus.DOWNLOADING.name();
    private static final String DOWNLOADED = BookStatus.DOWNLOADED.name();
    private static final String BOOK_INDEXING = BookStatus.INDEXING.name();
    private static final String WRITING = BatchStatus.WRITING.name();
    private static final String PUBLISHED = BatchStatus.PUBLISHED.name();

    /** The windows of the phases the lines show, ingestion first; a phase with no start or no end has none. */
    public static List<PhaseWindow> of(List<TransitionLine> lines) {
        return Stream.of(
                        window(INGESTION, lines, line -> line.isBook() && line.moves(QUEUED, DOWNLOADING),
                                line -> line.isBook() && line.moves(DOWNLOADING, DOWNLOADED)),
                        window(INDEXING, lines, line -> line.isBook() && line.moves(DOWNLOADED, BOOK_INDEXING),
                                line -> line.isBatch() && line.moves(WRITING, PUBLISHED)))
                .flatMap(Optional::stream)
                .toList();
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(start) && !instant.isAfter(end);
    }

    private static Optional<PhaseWindow> window(String phase, List<TransitionLine> lines, Predicate<TransitionLine> starts,
                                                Predicate<TransitionLine> ends) {
        Optional<Instant> start = lines.stream().filter(starts).map(TransitionLine::at).min(Comparator.naturalOrder());
        Optional<Instant> end = lines.stream().filter(ends).map(TransitionLine::at).max(Comparator.naturalOrder());
        return start.flatMap(first -> end.filter(last -> !last.isBefore(first)).map(last -> new PhaseWindow(phase, first, last)));
    }
}
