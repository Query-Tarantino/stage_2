package org.ulpgc.tarantino.indexer.benchmarks;

/**
 * What every indexer benchmark is measured with (SPEC §9): at least 5 warmup and 10 measurement iterations, three forks
 * so that the error also covers the differences between JVMs, and a fixed heap. Each benchmark only sets the length of
 * its iterations, which depends on how long one operation takes.
 */
final class BenchmarkProtocol {

    static final int WARMUP_ITERATIONS = 5;
    static final int MEASUREMENT_ITERATIONS = 10;
    static final int FORKS = 3;
    static final String FIXED_HEAP = "-Xmx1g";

    private BenchmarkProtocol() {
    }
}
