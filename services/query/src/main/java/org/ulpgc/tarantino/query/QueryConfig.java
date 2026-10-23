package org.ulpgc.tarantino.query;

import java.nio.file.Path;

public record QueryConfig(Path datamarts, String index, String metadata, String mongoUri, Path workload) {

    public static QueryConfig fromEnvironment() {
        return new QueryConfig(
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
