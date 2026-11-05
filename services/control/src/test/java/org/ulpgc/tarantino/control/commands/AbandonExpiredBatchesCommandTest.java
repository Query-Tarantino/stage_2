package org.ulpgc.tarantino.control.commands;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.operations.Transition;
import org.ulpgc.tarantino.control.model.GenerationBatch;
import org.ulpgc.tarantino.control.ports.BatchLeases;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbandonExpiredBatchesCommandTest {

    private final ExpiredBatches leases = new ExpiredBatches();
    private final RecordingTransitionLog log = new RecordingTransitionLog();
    private final AbandonExpiredBatchesCommand abandon = new AbandonExpiredBatchesCommand(leases, log);

    @Test
    void logsOnlyTheAbandonsThatMatchedWithTheirGeneration() {
        leases.expire(new GenerationBatch(1, "6531a0c2e4b0a1b2c3d4e5f6"), true);
        leases.expire(new GenerationBatch(2, "6531a0c2e4b0a1b2c3d4e5f7"), true);
        leases.expire(new GenerationBatch(2, "6531a0c2e4b0a1b2c3d4e5f8"), false);

        abandon.execute();

        assertEquals(List.of(Transition.batch("6531a0c2e4b0a1b2c3d4e5f6", BatchStatus.WRITING, BatchStatus.ABANDONED, 1),
                Transition.batch("6531a0c2e4b0a1b2c3d4e5f7", BatchStatus.WRITING, BatchStatus.ABANDONED, 2)),
                log.transitions());
    }

    private static final class ExpiredBatches implements BatchLeases {

        private final Map<GenerationBatch, Boolean> outcomes = new LinkedHashMap<>();

        void expire(GenerationBatch batch, boolean abandoned) {
            outcomes.put(batch, abandoned);
        }

        @Override
        public List<GenerationBatch> expired() {
            return List.copyOf(outcomes.keySet());
        }

        @Override
        public boolean abandon(GenerationBatch batch) {
            return outcomes.get(batch);
        }
    }
}
