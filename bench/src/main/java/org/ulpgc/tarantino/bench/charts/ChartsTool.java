package org.ulpgc.tarantino.bench.charts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.bench.containers.ContainerStats;
import org.ulpgc.tarantino.bench.containers.StatsTool;
import org.ulpgc.tarantino.bench.coordination.CoordinationTool;
import org.ulpgc.tarantino.bench.recovery.RecoveryChecks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code bench charts <run directory>} draws the charts of a run from the files it has (SPEC §9).
 * {@code bench recovery-chart <run directory>} draws into that run the recovery time of every recovery run next to it,
 * with the {@code run_id} of each one in {@code sources.txt} (SPEC §8).
 */
public final class ChartsTool {

    static final int DONE = 0;
    static final int FAILED = 1;
    static final int WRONG_USAGE = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(ChartsTool.class);
    private static final String HEADLESS = "java.awt.headless";
    private static final String SOURCES = "sources.txt";

    private ChartsTool() {
    }

    public static int run(List<String> args) {
        if (args.size() != 1) {
            LOGGER.error("Usage: bench charts <run directory>");
            return WRONG_USAGE;
        }
        System.setProperty(HEADLESS, Boolean.TRUE.toString());
        Path runDirectory = Path.of(args.getFirst());
        List<Path> charts = Stream.concat(coordination(runDirectory), resources(runDirectory)).toList();
        if (charts.isEmpty()) {
            LOGGER.error("{} has neither {} nor {} to draw", runDirectory, CoordinationTool.FILE_NAME, StatsTool.FILE_NAME);
            return FAILED;
        }
        charts.forEach(chart -> LOGGER.info("Wrote {}", chart));
        return DONE;
    }

    public static int recovery(List<String> args) {
        if (args.size() != 1) {
            LOGGER.error("Usage: bench recovery-chart <run directory>");
            return WRONG_USAGE;
        }
        System.setProperty(HEADLESS, Boolean.TRUE.toString());
        Path runDirectory = Path.of(args.getFirst()).toAbsolutePath();
        List<RecoveryChart.RecoveryRun> runs = RecoveryChart.latestRuns(runDirectory.getParent());
        if (runs.isEmpty()) {
            LOGGER.error("No recovery run next to {} has its {}", runDirectory, RecoveryChecks.FILE_NAME);
            return FAILED;
        }
        Path chart = RunChart.write(RecoveryChart.of(runs), runDirectory, RecoveryChart.NAME);
        writeSources(runDirectory.resolve(SOURCES), runs);
        LOGGER.info("Wrote the recovery time of {} runs to {}", runs.size(), chart);
        return DONE;
    }

    private static Stream<Path> coordination(Path runDirectory) {
        Path csv = runDirectory.resolve(CoordinationTool.FILE_NAME);
        if (!Files.exists(csv)) {
            return Stream.empty();
        }
        return Stream.of(RunChart.write(CoordinationChart.of(CoordinationChart.phases(lines(csv))), runDirectory, CoordinationChart.NAME));
    }

    private static Stream<Path> resources(Path runDirectory) {
        Path csv = runDirectory.resolve(StatsTool.FILE_NAME);
        if (!Files.exists(csv)) {
            return Stream.empty();
        }
        List<ContainerStats> samples = ResourceCharts.samples(lines(csv));
        return Stream.of(RunChart.write(ResourceCharts.cpu(samples), runDirectory, ResourceCharts.CPU),
                RunChart.write(ResourceCharts.memory(samples), runDirectory, ResourceCharts.MEMORY));
    }

    private static void writeSources(Path sources, List<RecoveryChart.RecoveryRun> runs) {
        try {
            Files.write(sources, runs.stream().map(RecoveryChart.RecoveryRun::runId).toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
