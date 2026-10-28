package org.ulpgc.tarantino.control.ports;

import java.util.OptionalInt;

public interface Rebuilds {

    /** The {@code target_generation} of the {@code RUNNING} rebuild, or empty when none runs. */
    OptionalInt runningTarget();

    /** {@code current.generation}, the one {@code query} serves. */
    int currentGeneration();

    /**
     * The condition of step 4 of SPEC §5.4: no book below the target except {@code FAILED} ones, and none of the target
     * in {@code DOWNLOADED} or {@code INDEXING}. Books without {@code generation} do not count.
     */
    boolean readyToSwitch(int target);

    /** “Switch generation” (SPEC §5.1): whether it matched, false if someone already switched it. */
    boolean switchTo(int target);

    /** “Finish the rebuild” (SPEC §5.1): whether it matched, false if it was already finished. */
    boolean finish(int target);
}
