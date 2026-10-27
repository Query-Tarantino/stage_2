package org.ulpgc.tarantino.bench.stats;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class SummaryStats {

    public record Metrics(
            int count,
            double mean,
            double stdDev,
            double ci95,
            long p50,
            long p95,
            long p99) {}

    public static Metrics calculate(List<Long> values) {
        if (values.isEmpty()) {
            return new Metrics(0, 0.0, 0.0, 0.0, 0, 0, 0);
        }
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int count = sorted.size();

        double sum = 0.0;
        for (long value : sorted) {
            sum += value;
        }
        double mean = sum / count;

        double sumSq = 0.0;
        for (long value : sorted) {
            double difference = value - mean;
            sumSq += difference * difference;
        }
        double stdDev = count > 1 ? Math.sqrt(sumSq / (count - 1)) : 0.0;
        double ci95 = count > 1 ? 1.96 * (stdDev / Math.sqrt(count)) : 0.0;

        long p50 = percentile(sorted, 50);
        long p95 = percentile(sorted, 95);
        long p99 = percentile(sorted, 99);

        return new Metrics(count, mean, stdDev, ci95, p50, p95, p99);
    }

    private static long percentile(List<Long> sorted, double percentage) {
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
            String line = String.format(Locale.ROOT, "%s,\"%s\",%.2f,%.2f,%.2f,%d,%d,%d,%s\n",
                    metric, configuration, metrics.mean(), metrics.stdDev(), metrics.ci95(), metrics.p50(), metrics.p95(), metrics.p99(), unit);
            writer.write(line);
        }
    }

    public static List<Long> readLatencies(Path requestsCsv) throws IOException {
        List<Long> latencies = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(requestsCsv, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 4 && "200".equals(parts[2].trim())) {
                    latencies.add(Long.parseLong(parts[3].trim()));
                }
            }
        }
        return latencies;
    }

    public static void main(String[] args) {
        try {
            Path runDir = Paths.get(args.length > 0 ? args[0] : "benchmarks/runs/latest");
            String config = args.length > 1 ? args[1] : "default";
            String sanitizedConfig = config.replaceAll("[^a-zA-Z0-9_=-]", "_");
            Path specificCsv = runDir.resolve("load").resolve(sanitizedConfig + ".csv");
            Path requestsCsv = args.length > 2
                    ? Paths.get(args[2])
                    : (Files.exists(specificCsv) ? specificCsv : runDir.resolve("load/requests.csv"));
            Path summaryCsv = runDir.resolve("summary.csv");

            List<Long> latencies = readLatencies(requestsCsv);
            Metrics metrics = calculate(latencies);
            appendToSummary(summaryCsv, "search_latency", config, metrics, "us");
        } catch (Exception exception) {
            exception.printStackTrace();
            System.exit(1);
        }
    }
}
