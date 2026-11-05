package org.ulpgc.tarantino.bench.recovery;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * {@code bench wait snapshot <books> [<timeout seconds>]} (SPEC §9): returns once the snapshot of every {@code query}
 * replica holds at least {@code <books>} books. A trial that measures publication visibility waits for it before saving
 * its logs, so every replica has logged the {@code snapshot} line of the last batch (SPEC §4.8), however late its
 * periodic refresh comes.
 */
public class SnapshotWait {

    public static final String SNAPSHOT = "snapshot";

    private final QueryReplicas replicas;
    private final Clock clock;
    private final Pause pause;

    public SnapshotWait(QueryReplicas replicas, Clock clock, Pause pause) {
        this.replicas = replicas;
        this.clock = clock;
        this.pause = pause;
    }

    /** Whether every replica reached {@code books} within {@code timeout}; with no replica to ask, none did. */
    public boolean awaitAtLeast(int books, Duration timeout) {
        Instant deadline = clock.instant().plus(timeout);
        while (clock.instant().isBefore(deadline)) {
            if (everyReplicaHolds(books)) {
                return true;
            }
            pause.sleep(WaitCommand.POLL);
        }
        return false;
    }

    private boolean everyReplicaHolds(int books) {
        List<Integer> held = replicas.snapshotBooks();
        return !held.isEmpty() && held.stream().allMatch(count -> count >= books);
    }
}
