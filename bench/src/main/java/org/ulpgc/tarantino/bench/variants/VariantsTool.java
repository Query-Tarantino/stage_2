package org.ulpgc.tarantino.bench.variants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.bench.charts.VariantsCharts;
import org.ulpgc.tarantino.bench.containers.ContainerStats;
import org.ulpgc.tarantino.bench.coordination.TransitionLine;
import org.ulpgc.tarantino.bench.coordination.TransitionLogs;
import org.ulpgc.tarantino.bench.run.TrialName;
import org.ulpgc.tarantino.bench.stats.SummaryStats;
import org.ulpgc.tarantino.bench.visibility.BatchVisibility;
import org.ulpgc.tarantino.bench.visibility.VisibilityTool;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code bench variants <run dir>}: merges the {@code summary.csv} of a {@code coordination-variants} run into
 * {@code variants.csv}, with the mode as the first column, plus {@code pipeline_throughput} and the mean CPU of each
 * service, and compares every pair of modes for each metric and replica count in {@code pairs.csv} (SPEC §9).
 */
public final class VariantsTool {

    public static final String VARIANTS_FILE = "variants.csv";
    public static final String PAIRS_FILE = "pairs.csv";
    public static final List<String> MODES = List.of("hazelcast", "activemq", "activemq-persistent", "polling", "orchestrator");

    private static final Logger LOGGER = LoggerFactory.getLogger(VariantsTool.class);
    private static final int DONE = 0;
    private static final int WRONG_USAGE = 2;
    private static final String MODE = "mode";
    private static final String REPLICAS = "replicas";
    private static final String STATS_FILE = "stats.csv";
    private static final Pattern SUMMARY_ROW = Pattern.compile("([^,]+),\"mode=([^,\"]+),([^\"]*)\",(.*)");
    private static final String SUMMARY_HEADER = "metric,configuration,mean,std_dev,ci_95,p50,p95,p99,unit";

    private VariantsTool() {
    }

    public static int run(List<String> args) {
        if (args.size() != 1) {
            LOGGER.error("Usage: bench variants <run directory>");
            return WRONG_USAGE;
        }
        Path runDirectory = Path.of(args.getFirst());
        List<TrialMetrics> trials = trials(runDirectory);
        List<ModePair> pairs = pairs(trials);
        write(runDirectory.resolve(VARIANTS_FILE), "mode," + SUMMARY_HEADER,
                Stream.concat(summaryRows(runDirectory), measuredRows(trials)).toList());
        write(runDirectory.resolve(PAIRS_FILE), ModePair.HEADER, pairs.stream().map(ModePair::row).toList());
        VariantsCharts.write(runDirectory, trials, pairs);
        LOGGER.info("Compared {} trials in {} pairs of modes", trials.size(), pairs.size());
        return DONE;
    }

    static List<TrialMetrics> trials(Path runDirectory) {
        Path logs = runDirectory.resolve(TransitionLogs.FOLDER);
        Map<String, List<BatchVisibility>> visibility = VisibilityTool.byTrial(logs);
        List<ContainerStats> stats = stats(runDirectory.resolve(STATS_FILE));
        return TransitionLogs.byTrial(logs).entrySet().stream()
                .filter(trial -> TrialName.factors(trial.getKey()).containsKey(MODE))
                .map(trial -> measure(trial.getKey(), trial.getValue(), visibility.getOrDefault(trial.getKey(), List.of()), stats))
                .toList();
    }

    private static TrialMetrics measure(String trial, List<TransitionLine> lines, List<BatchVisibility> visibility,
                                        List<ContainerStats> stats) {
        Map<String, String> factors = TrialName.factors(trial);
        return TrialMeasures.of(trial, factors.get(MODE), Integer.parseInt(factors.get(REPLICAS)), lines, visibility, stats);
    }

    static List<ModePair> pairs(List<TrialMetrics> trials) {
        List<ModePair> pairs = new ArrayList<>();
        for (VariantMetric metric : VariantMetric.values()) {
            for (int replicas : trials.stream().map(TrialMetrics::replicas).collect(Collectors.toCollection(TreeSet::new))) {
                for (int first = 0; first < MODES.size(); first++) {
                    for (int second = first + 1; second < MODES.size(); second++) {
                        pair(trials, metric, replicas, MODES.get(first), MODES.get(second)).ifPresent(pairs::add);
                    }
                }
            }
        }
        return pairs;
    }

    private static Optional<ModePair> pair(List<TrialMetrics> trials, VariantMetric metric, int replicas, String modeA, String modeB) {
        List<Double> valuesA = values(trials, metric, replicas, modeA);
        List<Double> valuesB = values(trials, metric, replicas, modeB);
        if (valuesA.isEmpty() || valuesB.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ModePair.of(metric, replicas, modeA, valuesA, modeB, valuesB));
    }

    public static List<Double> values(List<TrialMetrics> trials, VariantMetric metric, int replicas, String mode) {
        return trials.stream()
                .filter(trial -> trial.replicas() == replicas && trial.mode().equals(mode) && trial.values().containsKey(metric))
                .map(trial -> trial.values().get(metric))
                .toList();
    }

    private static Stream<String> summaryRows(Path runDirectory) {
        Path summary = runDirectory.resolve(SummaryStats.FILE_NAME);
        if (!Files.exists(summary)) {
            return Stream.empty();
        }
        return lines(summary).stream().map(SUMMARY_ROW::matcher).filter(Matcher::matches)
                .map(row -> row.group(2) + "," + row.group(1) + ",\"mode=" + row.group(2) + "," + row.group(3) + "\"," + row.group(4));
    }

    private static Stream<String> measuredRows(List<TrialMetrics> trials) {
        Map<String, List<TrialMetrics>> byConfiguration = trials.stream().collect(Collectors.groupingBy(
                trial -> trial.mode() + "," + trial.replicas(), TreeMap::new, Collectors.toList()));
        return byConfiguration.values().stream().flatMap(VariantsTool::measuredRowsOf);
    }

    private static Stream<String> measuredRowsOf(List<TrialMetrics> configuration) {
        VariantMetric throughput = VariantMetric.PIPELINE_THROUGHPUT;
        Stream<String> pipeline = Stream.of(row(configuration, throughput.column(),
                present(configuration, trial -> trial.values().get(throughput)), throughput.unit()));
        Stream<String> cpu = configuration.stream().flatMap(trial -> trial.cpuByService().keySet().stream()).distinct().sorted()
                .map(service -> row(configuration, "cpu_" + service, present(configuration, trial -> trial.cpuByService().get(service)), "%"));
        return Stream.concat(pipeline, cpu);
    }

    private static List<Double> present(List<TrialMetrics> configuration, Function<TrialMetrics, Double> value) {
        return configuration.stream().map(value).filter(Objects::nonNull).toList();
    }

    private static String row(List<TrialMetrics> configuration, String metric, List<Double> values, String unit) {
        TrialMetrics first = configuration.getFirst();
        SummaryStats.Metrics stats = SummaryStats.calculate(values);
        return String.format(Locale.ROOT, "%s,%s,\"mode=%s,replicas=%d\",%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%s", first.mode(), metric,
                first.mode(), first.replicas(), stats.mean(), stats.stdDev(), stats.ci95(), stats.p50(), stats.p95(), stats.p99(), unit);
    }

    private static List<ContainerStats> stats(Path csv) {
        if (!Files.exists(csv)) {
            return List.of();
        }
        return lines(csv).stream().skip(1).filter(line -> !line.isBlank()).map(ContainerStats::fromCsvRow).toList();
    }

    private static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path csv, String header, List<String> rows) {
        try {
            Files.writeString(csv, Stream.concat(Stream.of(header), rows.stream()).map(line -> line + "\n").collect(Collectors.joining()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
