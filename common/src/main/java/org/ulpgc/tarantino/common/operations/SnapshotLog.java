package org.ulpgc.tarantino.common.operations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;

/** Writes the INFO line of SPEC §4.8 for each batch that enters a {@code query} snapshot, from which {@code bench} measures visibility. */
public class SnapshotLog {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotLog.class);

    private final String replica;
    private final Clock clock;

    public SnapshotLog() {
        this(Replica.name(), Clock.systemUTC());
    }

    SnapshotLog(String replica, Clock clock) {
        this.replica = replica;
        this.clock = clock;
    }

    public void write(String batchId, int generation) {
        LOGGER.info(line(batchId, generation));
    }

    String line(String batchId, int generation) {
        return "snapshot batch_id=%s generation=%d at=%s replica=%s".formatted(batchId, generation, clock.instant(), replica);
    }
}
