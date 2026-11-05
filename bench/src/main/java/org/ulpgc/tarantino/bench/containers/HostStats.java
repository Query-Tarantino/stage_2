package org.ulpgc.tarantino.bench.containers;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * One sample of the whole host, a row of {@code host.csv} (SPEC §9): its CPU in percent of one core, as
 * {@code docker stats} counts it, and the memory in use, as the operating system of the machine that runs the script
 * reports them.
 */
public record HostStats(Instant at, double cpuPercent, long memoryUsedBytes) {

    public static final String FILE_NAME = "host.csv";
    public static final String CSV_HEADER = "instant,cpu_percent,memory_used_bytes";

    private static final String CSV_SEPARATOR = ",";
    private static final double PERCENT = 100;

    /** A sample from the system's figures; empty while it cannot give the CPU load yet, which it reports as negative. */
    public static Optional<HostStats> of(Instant at, double cpuLoad, int processors, long totalMemoryBytes, long freeMemoryBytes) {
        if (cpuLoad < 0) {
            return Optional.empty();
        }
        return Optional.of(new HostStats(at, cpuLoad * processors * PERCENT, totalMemoryBytes - freeMemoryBytes));
    }

    /** A row of {@code host.csv} as {@link #csvRow()} writes it. */
    public static HostStats fromCsvRow(String row) {
        String[] columns = row.split(CSV_SEPARATOR);
        return new HostStats(Instant.parse(columns[0]), Double.parseDouble(columns[1]), Long.parseLong(columns[2]));
    }

    public String csvRow() {
        return String.format(Locale.ROOT, "%s,%.2f,%d", at, cpuPercent, memoryUsedBytes);
    }
}
