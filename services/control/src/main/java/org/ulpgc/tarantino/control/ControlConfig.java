package org.ulpgc.tarantino.control;

import java.nio.file.Path;

/** {@code mirror} is the local Gutenberg mirror whose books can be the candidates, or null without one. */
public record ControlConfig(Path workload, Path mirror, int parallelDownloads, int indexBatch) {

    private static final String PARALLEL_DOWNLOADS = "TARANTINO_PARALLEL_DOWNLOADS";
    private static final String INDEX_BATCH = "TARANTINO_INDEX_BATCH";

    public static ControlConfig fromEnvironment() {
        String mirror = variable("TARANTINO_MIRROR", "");
        return new ControlConfig(
                Path.of(variable("TARANTINO_WORKLOAD", "workload")),
                mirror.isBlank() ? null : Path.of(mirror),
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
