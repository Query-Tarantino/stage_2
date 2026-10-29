package org.ulpgc.tarantino.bench.containers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One {@code docker stats} sample of a container, a row of {@code stats.csv} (SPEC §9). */
public record ContainerStats(Instant at, String container, double cpuPercent, long memoryBytes) {

    public static final String CSV_HEADER = "instant,container,cpu_percent,memory_bytes";

    private static final String CSV_SEPARATOR = ",";
    private static final Pattern PERCENT = Pattern.compile("([0-9.]+)%");
    private static final Pattern MEMORY_IN_USE = Pattern.compile("([0-9.]+)\\s*([A-Za-z]+)");
    private static final Map<String, Long> BYTES_PER_UNIT = Map.of(
            "B", 1L,
            "kB", 1_000L, "MB", 1_000_000L, "GB", 1_000_000_000L,
            "KiB", 1L << 10, "MiB", 1L << 20, "GiB", 1L << 30, "TiB", 1L << 40);

    /** A line of {@code docker stats --format "{{json .}}"}, ignoring the screen-clearing codes around it. */
    public static Optional<ContainerStats> parse(String line, Instant at) {
        int start = line.indexOf('{');
        int end = line.lastIndexOf('}');
        if (start < 0 || end < start) {
            return Optional.empty();
        }
        JsonNode sample = tree(line.substring(start, end + 1));
        Matcher cpu = PERCENT.matcher(sample.path("CPUPerc").asText());
        Matcher memory = MEMORY_IN_USE.matcher(sample.path("MemUsage").asText());
        if (!cpu.lookingAt() || !memory.lookingAt() || !BYTES_PER_UNIT.containsKey(memory.group(2))) {
            return Optional.empty();
        }
        long memoryBytes = Math.round(Double.parseDouble(memory.group(1)) * BYTES_PER_UNIT.get(memory.group(2)));
        return Optional.of(new ContainerStats(at, sample.path("Name").asText(), Double.parseDouble(cpu.group(1)), memoryBytes));
    }

    /** A row of {@code stats.csv} as {@link #csvRow()} writes it. */
    public static ContainerStats fromCsvRow(String row) {
        String[] columns = row.split(CSV_SEPARATOR);
        return new ContainerStats(Instant.parse(columns[0]), columns[1], Double.parseDouble(columns[2]), Long.parseLong(columns[3]));
    }

    public String csvRow() {
        return String.format(Locale.ROOT, "%s,%s,%.2f,%d", at, container, cpuPercent, memoryBytes);
    }

    private static JsonNode tree(String json) {
        try {
            return TarantinoJson.mapper().readTree(json);
        } catch (JsonProcessingException e) {
            return TarantinoJson.mapper().missingNode();
        }
    }
}
