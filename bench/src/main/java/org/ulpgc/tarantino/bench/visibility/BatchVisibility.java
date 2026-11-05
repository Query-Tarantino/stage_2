package org.ulpgc.tarantino.bench.visibility;

import org.ulpgc.tarantino.bench.coordination.TransitionLine;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** When one {@code query} replica saw a batch, against when the indexer published it (SPEC §4.8). */
public record BatchVisibility(String batchId, String replica, Instant publishedAt, Instant visibleAt) {

    private static final String PUBLISHED = "PUBLISHED";

    public Duration visibility() {
        return Duration.between(publishedAt, visibleAt);
    }

    /** One per batch and replica, joining each first {@code snapshot} line with its batch's {@code to=PUBLISHED} line. */
    public static List<BatchVisibility> of(List<TransitionLine> transitions, List<SnapshotLine> snapshots) {
        Map<String, Instant> publishedAt = transitions.stream()
                .filter(line -> line.isBatch() && PUBLISHED.equals(line.to()))
                .collect(Collectors.toMap(TransitionLine::id, TransitionLine::at, (first, second) -> first));
        return snapshots.stream()
                .filter(snapshot -> publishedAt.containsKey(snapshot.batchId()))
                .sorted(Comparator.comparing(SnapshotLine::at))
                .collect(Collectors.toMap(snapshot -> snapshot.batchId() + " " + snapshot.replica(),
                        snapshot -> new BatchVisibility(snapshot.batchId(), snapshot.replica(), publishedAt.get(snapshot.batchId()),
                                snapshot.at()), (first, second) -> first))
                .values().stream()
                .sorted(Comparator.comparing(BatchVisibility::publishedAt).thenComparing(BatchVisibility::replica))
                .toList();
    }
}
