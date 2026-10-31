package org.ulpgc.tarantino.bench.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.bench.containers.ContainerStats;
import org.ulpgc.tarantino.bench.containers.StatsTool;
import org.ulpgc.tarantino.bench.coordination.TransitionLogs;
import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code bench resources <run directory>}: appends to {@code summary.csv} the CPU per replica, the total CPU and the
 * memory per replica of each service in each phase of each configuration of a {@code resource-usage} run (SPEC §9).
 */
public final class ResourceTool {

    static final int DONE = 0;
    static final int FAILED = 1;
    static final int WRONG_USAGE = 2;
    static final String CPU_PER_REPLICA = "cpu_per_replica";
    static final String CPU_TOTAL = "cpu_total";
    static final String MEMORY_PER_REPLICA = "memory_per_replica";

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceTool.class);
    private static final String PERCENT = "percent";
    private static final String MEBIBYTES = "MiB";
    private static final String PHASE = ",phase=";
    private static final String SERVICE = ",service=";

    private ResourceTool() {
    }

    public static int run(List<String> args) {
        if (args.size() != 1) {
            LOGGER.error("Usage: bench resources <run directory>");
            return WRONG_USAGE;
        }
        Path runDirectory = Path.of(args.getFirst());
        Map<ResourceUsage.Key, ResourceUsage.Measures> usage = usage(runDirectory);
        if (usage.isEmpty()) {
            LOGGER.error("{} has no {} samples inside the phase windows of its trials", runDirectory, StatsTool.FILE_NAME);
            return FAILED;
        }
        usage.forEach((key, measures) -> appendRows(runDirectory.resolve(SummaryStats.FILE_NAME), key, measures));
        LOGGER.info("Summarised the resources of {} services, phases and configurations in {}", usage.size(), runDirectory.resolve(SummaryStats.FILE_NAME));
        return DONE;
    }

    /** The resources of a run with trials and {@code stats.csv}; empty without either. */
    public static Map<ResourceUsage.Key, ResourceUsage.Measures> usage(Path runDirectory) {
        Path stats = runDirectory.resolve(StatsTool.FILE_NAME);
        Path logs = runDirectory.resolve(TransitionLogs.FOLDER);
        if (!Files.exists(stats) || !Files.isDirectory(logs)) {
            return Map.of();
        }
        Map<String, List<PhaseWindow>> windowsByTrial = TransitionLogs.byTrial(logs).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, trial -> PhaseWindow.of(trial.getValue()), (first, second) -> first, LinkedHashMap::new));
        return ResourceUsage.of(samples(stats), windowsByTrial);
    }

    private static List<ContainerStats> samples(Path stats) {
        try {
            return Files.readAllLines(stats).stream().skip(1).map(ContainerStats::fromCsvRow).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void appendRows(Path summaryCsv, ResourceUsage.Key key, ResourceUsage.Measures measures) {
        String configuration = key.configuration() + PHASE + key.phase() + SERVICE + key.service();
        appendRow(summaryCsv, CPU_PER_REPLICA, configuration, measures.cpuPerReplica(), PERCENT);
        appendRow(summaryCsv, CPU_TOTAL, configuration, measures.cpuTotal(), PERCENT);
        appendRow(summaryCsv, MEMORY_PER_REPLICA, configuration, measures.memoryPerReplicaMib(), MEBIBYTES);
    }

    private static void appendRow(Path summaryCsv, String metric, String configuration, List<Double> values, String unit) {
        try {
            SummaryStats.appendToSummary(summaryCsv, metric, configuration, SummaryStats.calculate(values.stream().map(Math::round).toList()), unit);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
