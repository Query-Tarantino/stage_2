package org.ulpgc.tarantino.control.commands;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.control.model.GenerationBatch;
import org.ulpgc.tarantino.control.ports.AbandonedBatches;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeleteAbandonedPostingsCommandTest {

    private static final List<GenerationBatch> PAST_GRACE_PERIOD = List.of(
            new GenerationBatch(1, "6531a0c2e4b0a1b2c3d4e5f1"),
            new GenerationBatch(2, "6531a0c2e4b0a1b2c3d4e5f2"));

    @Test
    void deletesThePostingsOfEveryBatchPastTheGracePeriodOnEveryPass() {
        AbandonedLongAgo batches = new AbandonedLongAgo();
        DeleteAbandonedPostingsCommand delete = new DeleteAbandonedPostingsCommand(batches);

        delete.execute();
        delete.execute();

        assertEquals(Stream.concat(PAST_GRACE_PERIOD.stream(), PAST_GRACE_PERIOD.stream()).toList(), batches.deleted());
    }

    private static final class AbandonedLongAgo implements AbandonedBatches {

        private final List<GenerationBatch> deleted = new ArrayList<>();

        List<GenerationBatch> deleted() {
            return deleted;
        }

        @Override
        public List<GenerationBatch> pastGracePeriod() {
            return PAST_GRACE_PERIOD;
        }

        @Override
        public void deletePostings(GenerationBatch batch) {
            deleted.add(batch);
        }
    }
}
