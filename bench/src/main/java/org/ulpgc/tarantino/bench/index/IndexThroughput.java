package org.ulpgc.tarantino.bench.index;

import org.ulpgc.tarantino.bench.coordination.TransitionLine;
import org.ulpgc.tarantino.bench.resources.PhaseWindow;
import org.ulpgc.tarantino.bench.throughput.ThroughputWindow;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.model.BookStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * How fast a trial of an {@code index-throughput} run indexed its books (SPEC §9), from its transition lines (SPEC §4.8).
 * A trial is a rebuild, so it reads only the highest generation a batch was published in, whatever earlier generations
 * its logs still hold. The window runs from the first index claim to the last batch published, and it counts each book
 * published in that generation once.
 */
public final class IndexThroughput {

    private static final String WRITING = BatchStatus.WRITING.name();
    private static final String BATCH_PUBLISHED = BatchStatus.PUBLISHED.name();
    private static final String BOOK_PUBLISHED = BookStatus.PUBLISHED.name();

    private IndexThroughput() {
    }

    /** The throughput of a trial; empty when its lines have no published batch or their window lasts no time. */
    public static Optional<ThroughputWindow> of(List<TransitionLine> lines) {
        return lastPublishedGeneration(lines).flatMap(generation -> ofGeneration(inGeneration(lines, generation)));
    }

    private static Optional<Integer> lastPublishedGeneration(List<TransitionLine> lines) {
        return lines.stream().filter(line -> line.isBatch() && line.moves(WRITING, BATCH_PUBLISHED))
                .map(TransitionLine::generation).filter(Objects::nonNull).max(Integer::compare);
    }

    private static List<TransitionLine> inGeneration(List<TransitionLine> lines, int generation) {
        return lines.stream().filter(line -> Objects.equals(line.generation(), generation)).toList();
    }

    private static Optional<ThroughputWindow> ofGeneration(List<TransitionLine> lines) {
        int books = (int) lines.stream().filter(line -> line.isBook() && BOOK_PUBLISHED.equals(line.to()))
                .map(TransitionLine::id).distinct().count();
        return PhaseWindow.of(lines).stream().filter(phase -> phase.phase().equals(PhaseWindow.INDEXING)).findFirst()
                .map(indexing -> ThroughputWindow.between(indexing.start(), indexing.end(), books))
                .filter(window -> window.books() > 0 && window.seconds() > 0);
    }
}
