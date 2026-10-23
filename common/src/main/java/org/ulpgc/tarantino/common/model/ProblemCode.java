package org.ulpgc.tarantino.common.model;

public enum ProblemCode {
    MISSING_PARAMETER(400),
    INVALID_PARAMETER(400),
    BOOK_NOT_FOUND(404),
    NOT_DOWNLOADED(409),
    DEPENDENCY_UNAVAILABLE(503);

    private final int status;

    ProblemCode(int status) {
        this.status = status;
    }

    public int status() {
        return status;
    }
}
