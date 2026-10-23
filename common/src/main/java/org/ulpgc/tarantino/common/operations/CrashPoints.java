package org.ulpgc.tarantino.common.operations;

import org.ulpgc.tarantino.common.config.CrashPoint;

import java.util.Optional;

/** Stops the service the first time it reaches the point of {@code TARANTINO_CRASH_AT}, without hooks or finally (SPEC §8). */
public class CrashPoints {

    private static final int CRASH_STATUS = 1;

    private final Optional<CrashPoint> configured;
    private final Runnable halt;

    public CrashPoints(Optional<CrashPoint> configured) {
        this(configured, () -> Runtime.getRuntime().halt(CRASH_STATUS));
    }

    CrashPoints(Optional<CrashPoint> configured, Runnable halt) {
        this.configured = configured;
        this.halt = halt;
    }

    public void hit(CrashPoint point) {
        if (configured.filter(point::equals).isPresent()) {
            halt.run();
        }
    }
}
