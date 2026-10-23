package org.ulpgc.tarantino.indexer;

import java.nio.file.Path;

public record IndexerConfig(Path datalake, String datalakeLayout, Path datamarts, String index,
                            String metadata, String mongoUri, Path workload) {

    public static IndexerConfig fromEnvironment() {
        return new IndexerConfig(
                Path.of(variable("TARANTINO_DATALAKE", "datalake")),
                variable("TARANTINO_DATALAKE_LAYOUT", "time"),
                Path.of(variable("TARANTINO_DATAMARTS", "datamarts")),
                variable("TARANTINO_INDEX", "json"),
                variable("TARANTINO_METADATA", "sqlite"),
                variable("TARANTINO_MONGO_URI", "mongodb://localhost:27017"),
                Path.of(variable("TARANTINO_WORKLOAD", "workload")));
    }

    private static String variable(String name, String defaultValue) {
        return System.getenv().getOrDefault(name, defaultValue);
    }
}
