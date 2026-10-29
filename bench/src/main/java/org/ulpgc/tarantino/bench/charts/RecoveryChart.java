package org.ulpgc.tarantino.bench.charts;

import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.ulpgc.tarantino.bench.recovery.RecoveryChecks;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The recovery time of every recovery run (SPEC §8): one bar per script and replica count, from the
 * {@code recovery_seconds} of each {@code checks.json}, and no bar where a script has no run with those replicas. When
 * a script ran more than once with the same replicas, its latest run counts.
 */
final class RecoveryChart {

    static final String NAME = "recovery-seconds";

    private static final String RECOVERY_SECONDS = "recovery_seconds";
    private static final String TITLE = "Recovery time per script";
    private static final String X_AXIS = "Script";
    private static final String Y_AXIS = "Recovery time (s)";
    private static final String REPLICAS = "N = ";

    private RecoveryChart() {
    }

    record RecoveryRun(String runId, String script, int replicas, double recoverySeconds) {

        private Configuration configuration() {
            return new Configuration(script, replicas);
        }
    }

    private record Configuration(String script, int replicas) {
    }

    /** The latest run of each script and replica count in {@code runsDirectory}, by script and then replicas. */
    static List<RecoveryRun> latestRuns(Path runsDirectory) {
        try (Stream<Path> runs = Files.list(runsDirectory)) {
            return runs.filter(run -> Files.exists(run.resolve(RecoveryChecks.FILE_NAME)))
                    .flatMap(run -> recoveryRun(run).stream())
                    .collect(Collectors.toMap(RecoveryRun::configuration, Function.identity(),
                            (first, second) -> first.runId().compareTo(second.runId()) < 0 ? second : first))
                    .values().stream()
                    .sorted(Comparator.comparing(RecoveryRun::script).thenComparingInt(RecoveryRun::replicas))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static CategoryChart of(List<RecoveryRun> runs) {
        List<String> scripts = runs.stream().map(RecoveryRun::script).distinct().sorted().toList();
        CategoryChart chart = new CategoryChartBuilder().width(RunChart.WIDTH).height(RunChart.HEIGHT)
                .title(TITLE).xAxisTitle(X_AXIS).yAxisTitle(Y_AXIS).build();
        runs.stream()
                .collect(Collectors.groupingBy(RecoveryRun::replicas, TreeMap::new,
                        Collectors.toMap(RecoveryRun::script, RecoveryRun::recoverySeconds)))
                .forEach((replicas, secondsByScript) -> chart.addSeries(REPLICAS + replicas, scripts, seconds(scripts, secondsByScript)));
        return chart;
    }

    private static List<Double> seconds(List<String> scripts, Map<String, Double> secondsByScript) {
        return scripts.stream().map(secondsByScript::get).toList();
    }

    private static Optional<RecoveryRun> recoveryRun(Path run) {
        String runId = run.getFileName().toString();
        Matcher name = RecoveryChecks.RUN_NAME.matcher(runId);
        if (!name.matches()) {
            return Optional.empty();
        }
        return Optional.of(new RecoveryRun(runId, name.group(1), Integer.parseInt(name.group(2)), recoverySeconds(run)));
    }

    private static double recoverySeconds(Path run) {
        try {
            return TarantinoJson.mapper().readTree(Files.readString(run.resolve(RecoveryChecks.FILE_NAME))).path(RECOVERY_SECONDS).asDouble();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
