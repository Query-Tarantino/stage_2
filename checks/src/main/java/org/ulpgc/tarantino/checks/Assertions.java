package org.ulpgc.tarantino.checks;

import org.ulpgc.tarantino.common.model.ProblemCode;

/** The assertions of the functional API checks (SPEC §9): the first one that fails stops them with an {@link AssertionError}. */
final class Assertions {

    private static final String CODE = "code";
    private static final String PARAMETER = "parameter";

    private Assertions() {
    }

    static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Assertion condition was false");
        }
    }

    /** Problem Details with that {@code code} and its status (SPEC §6.6). */
    static void assertProblem(CheckedClient.Response response, ProblemCode code) {
        assertEquals(code.status(), response.status());
        assertField(response, CODE, code.name());
    }

    /** Problem Details with that {@code code}, its status and the {@code parameter} that failed (SPEC §6.6). */
    static void assertProblem(CheckedClient.Response response, ProblemCode code, String parameter) {
        assertProblem(response, code);
        assertField(response, PARAMETER, parameter);
    }

    private static void assertField(CheckedClient.Response response, String field, String expected) {
        assertTrue(response.json() != null && response.json().has(field));
        assertEquals(expected, response.json().get(field).asText());
    }
}
