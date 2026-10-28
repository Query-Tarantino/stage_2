package org.ulpgc.tarantino.bench.coordination;

import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.model.BookStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The queue wait and the work of each phase of a book and its time to publication, the rows of {@code coordination.csv}
 * (SPEC §9). Each phase is measured on the claim that succeeded: the last download claim before the book was first
 * downloaded and the claim into the batch that got published, which is when the book becomes searchable (SPEC §6.7).
 * The time to publication counts from the creation of the state, retries included. A book never published has no row.
 */
public record BookCoordination(int bookId, Duration downloadQueueWait, Duration download, Duration indexQueueWait,
                               Duration indexing, Duration timeToPublication) {

    private static final String QUEUED = BookStatus.QUEUED.name();
    private static final String DOWNLOADING = BookStatus.DOWNLOADING.name();
    private static final String DOWNLOADED = BookStatus.DOWNLOADED.name();
    private static final String INDEXING = BookStatus.INDEXING.name();
    private static final String BATCH_PUBLISHED = BatchStatus.PUBLISHED.name();

    /** Every published book of the run by ascending id. */
    public static List<BookCoordination> of(List<TransitionLine> lines) {
        Map<String, Instant> batchesPublishedAt = lines.stream()
                .filter(line -> line.isBatch() && BATCH_PUBLISHED.equals(line.to()))
                .collect(Collectors.toMap(TransitionLine::id, TransitionLine::at, (first, second) -> first.isBefore(second) ? first : second));
        return lines.stream()
                .filter(TransitionLine::isBook)
                .sorted(Comparator.comparing(TransitionLine::at))
                .collect(Collectors.groupingBy(line -> Integer.parseInt(line.id()), TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .flatMap(book -> of(book.getKey(), book.getValue(), batchesPublishedAt).stream())
                .toList();
    }

    private static Optional<BookCoordination> of(int bookId, List<TransitionLine> history, Map<String, Instant> batchesPublishedAt) {
        return first(history, line -> line.from() == null && QUEUED.equals(line.to())).flatMap(created ->
                downloadPhase(history).flatMap(download ->
                        indexPhase(history, batchesPublishedAt).map(indexing -> new BookCoordination(bookId,
                                download.queueWait(), download.work(), indexing.queueWait(), indexing.work(),
                                Duration.between(created.at(), indexing.done())))));
    }

    private static Optional<Phase> downloadPhase(List<TransitionLine> history) {
        return first(history, line -> line.moves(DOWNLOADING, DOWNLOADED)).flatMap(downloaded ->
                last(history, line -> isClaim(line, QUEUED, DOWNLOADING) && !line.at().isAfter(downloaded.at()))
                        .map(claim -> new Phase(claim, downloaded.at())));
    }

    private static Optional<Phase> indexPhase(List<TransitionLine> history, Map<String, Instant> batchesPublishedAt) {
        return first(history, line -> isClaim(line, DOWNLOADED, INDEXING) && batchesPublishedAt.containsKey(line.batchId()))
                .map(claim -> new Phase(claim, batchesPublishedAt.get(claim.batchId())));
    }

    private static boolean isClaim(TransitionLine line, String from, String to) {
        return line.moves(from, to) && line.enqueuedAt() != null;
    }

    private static Optional<TransitionLine> first(List<TransitionLine> history, Predicate<TransitionLine> matching) {
        return history.stream().filter(matching).findFirst();
    }

    private static Optional<TransitionLine> last(List<TransitionLine> history, Predicate<TransitionLine> matching) {
        return history.stream().filter(matching).reduce((earlier, later) -> later);
    }

    private record Phase(TransitionLine claim, Instant done) {

        Duration queueWait() {
            return Duration.between(claim.enqueuedAt(), claim.at());
        }

        Duration work() {
            return Duration.between(claim.at(), done);
        }
    }
}
