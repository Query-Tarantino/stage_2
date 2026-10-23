package org.ulpgc.tarantino.control;

import java.nio.file.Path;

public record ControlConfig(Path control, Path workload, int parallelDownloads, int indexBatch) {

    private static final String PARALLEL_DOWNLOADS = "TARANTINO_PARALLEL_DOWNLOADS";
    private static final String INDEX_BATCH = "TARANTINO_INDEX_BATCH";

    public static ControlConfig fromEnvironment() {
        return new ControlConfig(
                Path.of(variable("TARANTINO_CONTROL", "control")),
                Path.of(variable("TARANTINO_WORKLOAD", "workload")),
                positiveInteger(PARALLEL_DOWNLOADS, variable(PARALLEL_DOWNLOADS, "8")),
                positiveInteger(INDEX_BATCH, variable(INDEX_BATCH, "100")));
    }

    static int positiveInteger(String name, String value) {
        try {
            int number = Integer.parseInt(value.strip());
            if (number > 0) {
                return number;
            }
        } catch (NumberFormatException e) {
            // reported below with the offending value
        }
        throw new IllegalArgumentException("Unknown " + name + ": " + value + " (expected a positive integer)");
    }

    private static String variable(String name, String defaultValue) {
        return System.getenv().getOrDefault(name, defaultValue);
    }
}
