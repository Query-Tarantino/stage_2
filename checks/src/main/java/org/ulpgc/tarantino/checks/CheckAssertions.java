package org.ulpgc.tarantino.checks;

/** The assertions of the API checks (SPEC §6): every failure says what had to hold. */
final class CheckAssertions {

    private CheckAssertions() {
    }

    static void assertTrue(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError("Check failed: " + what);
        }
    }

    static void assertEquals(Object expected, Object actual, String what) {
        if (!expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }

    static void assertStatus(int expected, CheckedClient.Response response, String request) {
        assertEquals(expected, response.status(), "the status of " + request);
    }
}
