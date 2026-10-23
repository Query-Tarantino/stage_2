package org.ulpgc.tarantino.control.model;

public record Outcome(boolean succeeded, String detail) {

    public static Outcome success(String detail) {
        return new Outcome(true, detail);
    }

    public static Outcome failure(String detail) {
        return new Outcome(false, detail);
    }
}
