package org.ulpgc.tarantino.crawler.benchmarking.support.environment;

import java.nio.file.Path;

public final class BenchmarkPaths {

    // macOS Spotlight skips directories whose name ends in .noindex; elsewhere the name is just a name
    private static final String SCRATCH_DIRECTORY = "tmp.noindex";

    private BenchmarkPaths() {
    }

    public static Path benchmarks() {
        return Path.of(variable("TARANTINO_BENCHMARKS", "benchmarks"));
    }

    public static Path workload() {
        return Path.of(variable("TARANTINO_WORKLOAD", "workload"));
    }

    public static Path scratch(String name) {
        return scratchRoot().resolve(name);
    }

    public static Path scratchRoot() {
        return benchmarks().resolve(SCRATCH_DIRECTORY);
    }

    public static String mongoUri(String database) {
        return variable("TARANTINO_MONGO_URI", "mongodb://localhost:27017").replaceAll("/+$", "") + "/" + database;
    }

    private static String variable(String name, String defaultValue) {
        return System.getenv().getOrDefault(name, defaultValue);
    }
}
