package org.ulpgc.tarantino.crawler.benchmarking.support.validation;

public final class Check {

    private Check() {
    }

    public static void require(boolean condition, String failure) {
        if (!condition) {
            throw new IllegalStateException("Invalid benchmark result: " + failure);
        }
    }
}
