package org.ulpgc.tarantino.control.commands;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.control.ports.Rebuilds;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SwitchGenerationCommandTest {

    @Test
    void switchesAndFinishesOnceTheTargetIsReady() {
        PointerAndRebuild state = new PointerAndRebuild(OptionalInt.of(2), 1, true);

        new SwitchGenerationCommand(state).execute();

        assertEquals(2, state.current);
        assertEquals(OptionalInt.empty(), state.running);
    }

    @Test
    void waitsWhileTheTargetIsNotReady() {
        PointerAndRebuild state = new PointerAndRebuild(OptionalInt.of(2), 1, false);

        new SwitchGenerationCommand(state).execute();

        assertEquals(1, state.current);
        assertEquals(OptionalInt.of(2), state.running);
    }

    @Test
    void onlyFinishesWhenACrashLeftThePointerSwitched() {
        PointerAndRebuild state = new PointerAndRebuild(OptionalInt.of(2), 2, false);

        new SwitchGenerationCommand(state).execute();

        assertEquals(2, state.current);
        assertEquals(OptionalInt.empty(), state.running);
    }

    @Test
    void leavesThePointerAloneWithoutARunningRebuild() {
        PointerAndRebuild state = new PointerAndRebuild(OptionalInt.empty(), 1, true);

        new SwitchGenerationCommand(state).execute();

        assertEquals(1, state.current);
    }

    private static final class PointerAndRebuild implements Rebuilds {

        private OptionalInt running;
        private int current;
        private final boolean ready;

        PointerAndRebuild(OptionalInt running, int current, boolean ready) {
            this.running = running;
            this.current = current;
            this.ready = ready;
        }

        @Override
        public OptionalInt runningTarget() {
            return running;
        }

        @Override
        public int currentGeneration() {
            return current;
        }

        @Override
        public boolean readyToSwitch(int target) {
            return ready;
        }

        @Override
        public boolean switchTo(int target) {
            if (current != target - 1) {
                return false;
            }
            current = target;
            return true;
        }

        @Override
        public boolean finish(int target) {
            if (running.isEmpty() || running.getAsInt() != target) {
                return false;
            }
            running = OptionalInt.empty();
            return true;
        }
    }
}
