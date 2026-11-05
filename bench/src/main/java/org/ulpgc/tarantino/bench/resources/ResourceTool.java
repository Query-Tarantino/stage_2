package org.ulpgc.tarantino.bench.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.bench.containers.ContainerStats;
import org.ulpgc.tarantino.bench.containers.HostStats;
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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@code bench resources <run directory>}: appends to {@code summary.csv} the CPU per replica, the total CPU and the
 * memory per replica of each service, the CPU and memory of the whole stack and, when the run has {@code host.csv}, of
 * the whole host, in each phase of each configuration of a {@code resource-usage} run, over its repetitions (SPEC §9).
 */
public final class ResourceTool {

    static final int DONE = 0;
    static final int FAILED = 1;
    static final int WRONG_USAGE = 2;
    static final String CPU_PER_REPLICA = "cpu_per_replica";
    static final String CPU_TOTAL = "cpu_total";
    static final String MEMORY_PER_REPLICA = "memory_per_replica";
    static final String STACK_CPU_TOTAL = "stack_cpu_total";
    static final String STACK_MEMORY_TOTAL = "stack_memory_total";
    static final String HOST_CPU_TOTAL = "host_cpu_total";
    static final String HOST_MEMORY_USED = "host_memory_used";

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
        Map<ResourceUsage.Key, List<ResourceUsage.Measures>> usage = usage(runDirectory);
        if (usage.isEmpty()) {
            LOGGER.error("{} has no {} samples inside the phase windows of its trials", runDirectory, StatsTool.FILE_NAME);
            return FAILED;
        }
        Path summaryCsv = runDirectory.resolve(SummaryStats.FILE_NAME);
        usage.forEach((key, repetitions) -> appendServiceRows(summaryCsv, key, repetitions));
        usage(runDirectory, ResourceUsage::stackPerRepetition).forEach((key, repetitions) -> appendStackRows(summaryCsv, key, repetitions));
        appendHostRows(runDirectory, summaryCsv);
        LOGGER.info("Summarised the resources of {} services, phases and configurations in {}", usage.size(), summaryCsv);
        return DONE;
    }

    /** The resources of each service per repetition of a run with trials and {@code stats.csv}; empty without either. */
    public static Map<ResourceUsage.Key, List<ResourceUsage.Measures>> usage(Path runDirectory) {
        return usage(runDirectory, ResourceUsage::perRepetition);
    }

    private static Map<ResourceUsage.Key, List<ResourceUsage.Measures>> usage(Path runDirectory,
            BiFunction<List<ContainerStats>, Map<String, List<PhaseWindow>>, Map<ResourceUsage.Key, List<ResourceUsage.Measures>>> measures) {
        Path stats = runDirectory.resolve(StatsTool.FILE_NAME);
        Path logs = runDirectory.resolve(TransitionLogs.FOLDER);
        if (!Files.exists(stats) || !Files.isDirectory(logs)) {
            return Map.of();
        }
        return measures.apply(rows(stats).stream().map(ContainerStats::fromCsvRow).toList(), windowsByTrial(logs));
    }

    private static Map<String, List<PhaseWindow>> windowsByTrial(Path logs) {
        return TransitionLogs.byTrial(logs).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, trial -> PhaseWindow.of(trial.getValue()), (first, second) -> first, LinkedHashMap::new));
    }

    private static List<String> rows(Path csv) {
        try {
            return Files.readAllLines(csv).stream().skip(1).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void appendHostRows(Path runDirectory, Path summaryCsv) {
        Path host = runDirectory.resolve(HostStats.FILE_NAME);
        if (!Files.exists(host)) {
            return;
        }
        List<HostStats> samples = rows(host).stream().map(HostStats::fromCsvRow).toList();
        HostUsage.perRepetition(samples, windowsByTrial(runDirectory.resolve(TransitionLogs.FOLDER))).forEach((key, repetitions) -> {
            String configuration = key.configuration() + PHASE + key.phase();
            appendRow(summaryCsv, HOST_CPU_TOTAL, configuration, repetitions.stream().map(HostUsage.Measures::cpuPercent).toList(), PERCENT);
            appendRow(summaryCsv, HOST_MEMORY_USED, configuration, repetitions.stream().map(HostUsage.Measures::memoryUsedMib).toList(), MEBIBYTES);
        });
    }

    private static void appendServiceRows(Path summaryCsv, ResourceUsage.Key key, List<ResourceUsage.Measures> repetitions) {
        String configuration = key.configuration() + PHASE + key.phase() + SERVICE + key.service();
        appendRow(summaryCsv, CPU_PER_REPLICA, configuration, values(repetitions, ResourceUsage.Measures::cpuPerReplica), PERCENT);
        appendRow(summaryCsv, CPU_TOTAL, configuration, values(repetitions, ResourceUsage.Measures::cpuTotal), PERCENT);
        appendRow(summaryCsv, MEMORY_PER_REPLICA, configuration, values(repetitions, ResourceUsage.Measures::memoryPerReplicaMib), MEBIBYTES);
    }

    private static void appendStackRows(Path summaryCsv, ResourceUsage.Key key, List<ResourceUsage.Measures> repetitions) {
        String configuration = key.configuration() + PHASE + key.phase();
        appendRow(summaryCsv, STACK_CPU_TOTAL, configuration, values(repetitions, ResourceUsage.Measures::cpuTotal), PERCENT);
        appendRow(summaryCsv, STACK_MEMORY_TOTAL, configuration, values(repetitions, ResourceUsage.Measures::memoryTotalMib), MEBIBYTES);
    }

    private static List<List<Double>> values(List<ResourceUsage.Measures> repetitions, Function<ResourceUsage.Measures, List<Double>> values) {
        return repetitions.stream().map(values).toList();
    }

    private static void appendRow(Path summaryCsv, String metric, String configuration, List<List<Double>> repetitions, String unit) {
        try {
            SummaryStats.appendToSummary(summaryCsv, metric, configuration, SummaryStats.ofRepetitions(repetitions), unit);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
