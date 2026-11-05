package org.ulpgc.tarantino.control.commands;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.control.ports.OldGenerations;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DropOldGenerationsCommandTest {

    @Test
    void dropsEveryExistingGenerationBelowASettledCurrentOne() {
        ThreeGenerations generations = new ThreeGenerations(OptionalInt.of(3));

        new DropOldGenerationsCommand(generations).execute();

        assertEquals(List.of(1, 2), generations.dropped());
    }

    @Test
    void dropsNothingWithinTheGracePeriod() {
        ThreeGenerations generations = new ThreeGenerations(OptionalInt.empty());

        new DropOldGenerationsCommand(generations).execute();

        assertEquals(List.of(), generations.dropped());
    }

    private static final class ThreeGenerations implements OldGenerations {

        private final OptionalInt settledCurrent;
        private final List<Integer> dropped = new ArrayList<>();

        ThreeGenerations(OptionalInt settledCurrent) {
            this.settledCurrent = settledCurrent;
        }

        List<Integer> dropped() {
            return dropped;
        }

        @Override
        public OptionalInt settledCurrent() {
            return settledCurrent;
        }

        @Override
        public List<Integer> existingBefore(int generation) {
            return List.of(1, 2, 3).stream().filter(existing -> existing < generation).toList();
        }

        @Override
        public void drop(int generation) {
            dropped.add(generation);
        }
    }
}
