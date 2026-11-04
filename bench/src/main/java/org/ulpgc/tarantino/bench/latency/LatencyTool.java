package org.ulpgc.tarantino.bench.latency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.bench.run.TrialName;
import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code bench search-latency <run directory>}: appends to {@code summary.csv} the latency and the throughput of each
 * configuration of a {@code search-latency} run over its repetitions (SPEC §9). Each repetition gives one value per
 * metric, from its {@code load/<trial>.csv.gz}, so the 95 % CI shows how much the repetitions differ and not how many
 * requests they sent.
 */
public final class LatencyTool {

    static final int DONE = 0;
    static final int FAILED = 1;
    static final int WRONG_USAGE = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(LatencyTool.class);
    private static final String LOAD = "load";
    private static final String TRIAL_FILE = ".csv.gz";
    private static final String MICROSECONDS = "us";
    private static final String REQUESTS_PER_SECOND = "requests/s";
    private static final List<Metric> METRICS = List.of(
            new Metric("search_latency_mean", MICROSECONDS, trial -> trial.latency().mean()),
            new Metric("search_latency_p50", MICROSECONDS, trial -> trial.latency().p50()),
            new Metric("search_latency_p95", MICROSECONDS, trial -> trial.latency().p95()),
            new Metric("search_latency_p99", MICROSECONDS, trial -> trial.latency().p99()),
            new Metric("search_throughput", REQUESTS_PER_SECOND, TrialLoad::requestsPerSecond));

    private record Metric(String name, String unit, ToDoubleFunction<TrialLoad> value) {
    }

    private LatencyTool() {
    }

    public static int run(List<String> args) {
        if (args.size() != 1) {
            LOGGER.error("Usage: bench search-latency <run directory>");
            return WRONG_USAGE;
        }
        Path runDirectory = Path.of(args.getFirst());
        Map<String, List<TrialLoad>> repetitionsByConfiguration = repetitionsByConfiguration(runDirectory.resolve(LOAD));
        if (repetitionsByConfiguration.isEmpty()) {
            LOGGER.error("{} has no trial in {}/<trial>{}", runDirectory, LOAD, TRIAL_FILE);
            return FAILED;
        }
        Path summaryCsv = runDirectory.resolve(SummaryStats.FILE_NAME);
        repetitionsByConfiguration.forEach((configuration, repetitions) ->
                METRICS.forEach(metric -> append(summaryCsv, metric, configuration, repetitions)));
        LOGGER.info("Summarised {} configurations over their repetitions in {}", repetitionsByConfiguration.size(), summaryCsv);
        return DONE;
    }

    private static Map<String, List<TrialLoad>> repetitionsByConfiguration(Path load) {
        if (!Files.isDirectory(load)) {
            return Map.of();
        }
        try (Stream<Path> files = Files.list(load)) {
            return files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(TRIAL_FILE))
                    .map(name -> name.substring(0, name.length() - TRIAL_FILE.length()))
                    .filter(TrialName::isValid)
                    .sorted(TrialName.ORDER)
                    .collect(Collectors.groupingBy(TrialName::configuration, LinkedHashMap::new,
                            Collectors.mapping(trial -> TrialLoad.read(load.resolve(trial + TRIAL_FILE)), Collectors.toList())));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void append(Path summaryCsv, Metric metric, String configuration, List<TrialLoad> repetitions) {
        try {
            SummaryStats.appendToSummary(summaryCsv, metric.name(), configuration,
                    SummaryStats.calculate(repetitions.stream().map(metric.value()::applyAsDouble).toList()), metric.unit());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
