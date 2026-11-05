package org.ulpgc.tarantino.control.commands;

import org.ulpgc.tarantino.common.operations.Transition;
import org.ulpgc.tarantino.common.operations.TransitionLog;

import java.util.ArrayList;
import java.util.List;

/** Keeps every transition a command writes, so its tests can check one line per matching write (SPEC §4.8). */
class RecordingTransitionLog extends TransitionLog {

    private final List<Transition> transitions = new ArrayList<>();

    @Override
    public void write(Transition transition) {
        transitions.add(transition);
    }

    List<Transition> transitions() {
        return transitions;
    }
}
