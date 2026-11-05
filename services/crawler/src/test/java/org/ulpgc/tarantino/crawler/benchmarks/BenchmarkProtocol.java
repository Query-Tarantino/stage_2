package org.ulpgc.tarantino.crawler.benchmarks;

/**
 * What every crawler benchmark is measured with (SPEC §9): at least 5 warmup and 10 measurement iterations, three forks
 * so that the error also covers the differences between JVMs, and the fixed heap the crawler gets in Compose (§2.4).
 * Each benchmark only sets the length of its iterations, which depends on how long one operation takes.
 */
final class BenchmarkProtocol {

    static final int WARMUP_ITERATIONS = 5;
    static final int MEASUREMENT_ITERATIONS = 10;
    static final int FORKS = 3;
    static final String FIXED_HEAP = "-Xmx512m";

    private BenchmarkProtocol() {
    }
}
