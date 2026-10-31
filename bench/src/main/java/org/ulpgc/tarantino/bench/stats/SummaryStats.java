package org.ulpgc.tarantino.bench.stats;

import org.apache.commons.math3.distribution.TDistribution;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;

/**
 * The figures of {@code summary.csv} (SPEC §9): mean, sample standard deviation, the half-width of the 95 % CI of the
 * mean with Student's t for {@code count - 1} degrees of freedom, and nearest-rank percentiles.
 */
public class SummaryStats {

    public static final String FILE_NAME = "summary.csv";

    private static final double CONFIDENCE = 0.95;
    private static final double UPPER_TAIL = 1 - (1 - CONFIDENCE) / 2;

    public record Metrics(
            int count,
            double mean,
            double stdDev,
            double ci95,
            double p50,
            double p95,
            double p99) {}

    public static Metrics calculate(List<? extends Number> values) {
        if (values.isEmpty()) {
            return new Metrics(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
        List<Double> sorted = values.stream().map(Number::doubleValue).sorted().toList();
        int count = sorted.size();

        double sum = sorted.stream().mapToDouble(Double::doubleValue).sum();
        double mean = sum / count;

        double sumSq = sorted.stream()
                .mapToDouble(value -> (value - mean) * (value - mean))
                .sum();
        double stdDev = count > 1 ? Math.sqrt(sumSq / (count - 1)) : 0.0;
        double ci95 = count > 1 ? studentT(count - 1) * (stdDev / Math.sqrt(count)) : 0.0;

        double p50 = percentile(sorted, 50);
        double p95 = percentile(sorted, 95);
        double p99 = percentile(sorted, 99);

        return new Metrics(count, mean, stdDev, ci95, p50, p95, p99);
    }

    private static double studentT(int degreesOfFreedom) {
        return new TDistribution(degreesOfFreedom).inverseCumulativeProbability(UPPER_TAIL);
    }

    private static double percentile(List<Double> sorted, double percentage) {
        int index = (int) Math.ceil((percentage / 100.0) * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    public static void appendToSummary(Path summaryCsv, String metric, String configuration, Metrics metrics, String unit) throws IOException {
        boolean exists = Files.exists(summaryCsv);
        try (BufferedWriter writer = Files.newBufferedWriter(
                summaryCsv,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            if (!exists) {
                writer.write("metric,configuration,mean,std_dev,ci_95,p50,p95,p99,unit\n");
            }
            String line = String.format(Locale.ROOT, "%s,\"%s\",%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%s\n",
                    metric, configuration, metrics.mean(), metrics.stdDev(), metrics.ci95(), metrics.p50(), metrics.p95(), metrics.p99(), unit);
            writer.write(line);
        }
    }

    public static List<Long> readLatencies(Path requestsCsv) throws IOException {
        try (var lines = Files.lines(requestsCsv, StandardCharsets.UTF_8)) {
            return lines.skip(1)
                    .map(line -> line.split(","))
                    .filter(parts -> parts.length >= 4 && "200".equals(parts[2].trim()))
                    .map(parts -> Long.parseLong(parts[3].trim()))
                    .toList();
        }
    }

    public static void main(String[] args) {
        try {
            Path runDir = Paths.get(args.length > 0 ? args[0] : "benchmarks/runs/latest");
            String config = args.length > 1 ? args[1] : "default";
            String sanitizedConfig = config.replaceAll("[^a-zA-Z0-9_=-]", "_");
            Path specificCsv = runDir.resolve("load").resolve(sanitizedConfig + ".csv");
            Path requestsCsv = args.length > 2 && !args[2].isBlank()
                    ? Paths.get(args[2])
                    : (Files.exists(specificCsv) ? specificCsv : runDir.resolve("load/requests.csv"));
            String metricName = args.length > 3 && !args[3].isBlank() ? args[3] : "search_latency";
            Path summaryCsv = runDir.resolve(FILE_NAME);

            List<Long> latencies = readLatencies(requestsCsv);
            Metrics metrics = calculate(latencies);
            appendToSummary(summaryCsv, metricName, config, metrics, "us");
        } catch (Exception exception) {
            exception.printStackTrace();
            System.exit(1);
        }
    }
}
