package org.ulpgc.tarantino.control.commands;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.control.ports.PublishedBatches;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnnouncePublishedBatchesCommandTest {

    private final Map<Integer, Set<String>> published = new HashMap<>();
    private final AtomicInteger refreshes = new AtomicInteger();
    private int generation = 1;

    @Test
    void refreshesOncePerTickWithNewBatchesAndStartsOverWhenTheGenerationChanges() {
        AnnouncePublishedBatchesCommand announce = new AnnouncePublishedBatchesCommand(new InMemoryBatches(), refreshes::incrementAndGet);
        publish(1, "a");
        publish(1, "b");
        announce.execute();
        assertEquals(1, refreshes.get());

        publish(1, "c");
        announce.execute();
        announce.execute();
        assertEquals(2, refreshes.get());

        publish(2, "a");
        publish(2, "b");
        generation = 2;
        announce.execute();

        assertEquals(3, refreshes.get());
    }

    private void publish(int batchGeneration, String batchId) {
        published.computeIfAbsent(batchGeneration, key -> new HashSet<>()).add(batchId);
    }

    private final class InMemoryBatches implements PublishedBatches {

        @Override
        public int currentGeneration() {
            return generation;
        }

        @Override
        public Set<String> publishedBatchIds(int batchGeneration) {
            return Set.copyOf(published.getOrDefault(batchGeneration, Set.of()));
        }
    }
}
