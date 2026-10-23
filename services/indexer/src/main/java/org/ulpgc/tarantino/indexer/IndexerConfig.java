package org.ulpgc.tarantino.indexer;

import java.nio.file.Path;

public record IndexerConfig(Path datalake, String mongoUri, Path workload) {

    public static IndexerConfig fromEnvironment() {
        return new IndexerConfig(
                Path.of(variable("TARANTINO_DATALAKE", "datalake")),
                variable("TARANTINO_MONGO_URI", "mongodb://localhost:27017"),
                Path.of(variable("TARANTINO_WORKLOAD", "workload")));
    }

    private static String variable(String name, String defaultValue) {
        return System.getenv().getOrDefault(name, defaultValue);
    }
}
