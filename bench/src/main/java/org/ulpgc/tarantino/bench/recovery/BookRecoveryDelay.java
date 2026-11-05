package org.ulpgc.tarantino.bench.recovery;

import org.ulpgc.tarantino.bench.coordination.TransitionLine;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * How long one book took to come back after a failure: to the first line with equal {@code from} and {@code to} after
 * {@code failed_at}, a requeue or a dispatch again, and to its next claim after that, or after {@code failed_at} when it
 * had no such line (SPEC §4.8).
 */
public record BookRecoveryDelay(int bookId, String status, Optional<Duration> requeueDelay, Duration reclaimDelay) {

    private static final Map<String, String> CLAIMED_FROM = Map.of("DOWNLOADING", "QUEUED", "INDEXING", "DOWNLOADED");

    /** One per book that was requeued or claimed after {@code failedAt}, by ascending {@code book_id}. */
    public static List<BookRecoveryDelay> of(List<TransitionLine> transitions, Instant failedAt) {
        List<TransitionLine> after = transitions.stream()
                .filter(line -> line.isBook() && line.at().isAfter(failedAt))
                .sorted(Comparator.comparing(TransitionLine::at))
                .toList();
        Map<String, TransitionLine> firstRequeue = first(after.stream().filter(BookRecoveryDelay::isRequeue));
        return after.stream()
                .map(TransitionLine::id)
                .distinct()
                .flatMap(bookId -> delay(bookId, Optional.ofNullable(firstRequeue.get(bookId)), after, failedAt).stream())
                .sorted(Comparator.comparingInt(BookRecoveryDelay::bookId))
                .toList();
    }

    private static Optional<BookRecoveryDelay> delay(String bookId, Optional<TransitionLine> requeue, List<TransitionLine> after,
                                                     Instant failedAt) {
        Instant from = requeue.map(TransitionLine::at).orElse(failedAt);
        return after.stream()
                .filter(line -> line.id().equals(bookId) && isClaim(line) && line.at().isAfter(from))
                .filter(line -> requeue.map(requeued -> requeued.from().equals(CLAIMED_FROM.get(line.to()))).orElse(true))
                .findFirst()
                .map(claim -> new BookRecoveryDelay(Integer.parseInt(bookId), CLAIMED_FROM.get(claim.to()),
                        requeue.map(requeued -> Duration.between(failedAt, requeued.at())), Duration.between(failedAt, claim.at())));
    }

    private static Map<String, TransitionLine> first(Stream<TransitionLine> lines) {
        return lines.collect(Collectors.toMap(TransitionLine::id, Function.identity(), (first, second) -> first));
    }

    private static boolean isRequeue(TransitionLine line) {
        return line.from() != null && line.from().equals(line.to());
    }

    private static boolean isClaim(TransitionLine line) {
        return line.from() != null && line.from().equals(CLAIMED_FROM.get(line.to()));
    }
}
