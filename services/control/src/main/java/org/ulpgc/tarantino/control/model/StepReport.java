package org.ulpgc.tarantino.control.model;

public record StepReport(NextStep step, Outcome outcome) {

    public boolean idle() {
        return step.action() == NextStep.Action.IDLE;
    }

    public String description() {
        return "%s %s: %s".formatted(step.action(), step.books(), outcome.detail());
    }
}
