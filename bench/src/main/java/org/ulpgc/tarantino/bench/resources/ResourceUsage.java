package org.ulpgc.tarantino.bench.resources;

import org.ulpgc.tarantino.bench.containers.ComposeReplica;
import org.ulpgc.tarantino.bench.containers.ContainerStats;
import org.ulpgc.tarantino.bench.run.TrialName;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The CPU and memory of each Compose service in each phase of each configuration of a run with trials (SPEC §9), from
 * the {@code stats.csv} samples inside the phase windows, kept apart per repetition. A refresh of {@code docker stats}
 * lists each container once, so a service's total CPU and memory in a refresh are the sums over its replicas, and the
 * stack's are the sums over every container of the stack.
 */
public final class ResourceUsage {

    /** The service of the keys that add up every container of the stack. */
    public static final String STACK = "stack";

    static final double BYTES_PER_MIB = 1 << 20;

    private ResourceUsage() {
    }

    public record Key(String configuration, String phase, String service) {
    }

    public record Measures(List<Double> cpuPerReplica, List<Double> cpuTotal, List<Double> memoryPerReplicaMib,
                           List<Double> memoryTotalMib) {

        static Measures ofRefresh(List<ContainerStats> replicas) {
            List<Double> memory = replicas.stream().map(replica -> replica.memoryBytes() / BYTES_PER_MIB).toList();
            return new Measures(replicas.stream().map(ContainerStats::cpuPercent).toList(),
                    List.of(replicas.stream().mapToDouble(ContainerStats::cpuPercent).sum()), memory,
                    List.of(memory.stream().mapToDouble(Double::doubleValue).sum()));
        }

        Measures plus(Measures other) {
            return new Measures(concat(cpuPerReplica, other.cpuPerReplica), concat(cpuTotal, other.cpuTotal),
                    concat(memoryPerReplicaMib, other.memoryPerReplicaMib), concat(memoryTotalMib, other.memoryTotalMib));
        }

        private static List<Double> concat(List<Double> first, List<Double> second) {
            return Stream.concat(first.stream(), second.stream()).toList();
        }
    }

    /** The measures of each service, one per repetition in trial order; keys by configuration, phase and service name. */
    public static Map<Key, List<Measures>> perRepetition(List<ContainerStats> samples, Map<String, List<PhaseWindow>> windowsByTrial) {
        return perRepetition(refreshes(samples), windowsByTrial, ResourceUsage::measuresByService);
    }

    /** The measures of the whole stack, under {@link #STACK}, one per repetition in trial order. */
    public static Map<Key, List<Measures>> stackPerRepetition(List<ContainerStats> samples, Map<String, List<PhaseWindow>> windowsByTrial) {
        return perRepetition(refreshes(samples), windowsByTrial, ResourceUsage::measuresOfStack);
    }

    private static Map<Key, List<Measures>> perRepetition(List<List<ContainerStats>> refreshes, Map<String, List<PhaseWindow>> windowsByTrial,
                                                          BiFunction<List<List<ContainerStats>>, PhaseWindow, Map<String, Measures>> measures) {
        Map<Key, List<Measures>> usage = new LinkedHashMap<>();
        windowsByTrial.forEach((trial, windows) -> repetition(TrialName.configuration(trial), windows, refreshes, measures)
                .forEach((key, repetition) -> usage.computeIfAbsent(key, ignored -> new ArrayList<>()).add(repetition)));
        return usage;
    }

    private static Map<Key, Measures> repetition(String configuration, List<PhaseWindow> windows, List<List<ContainerStats>> refreshes,
                                                 BiFunction<List<List<ContainerStats>>, PhaseWindow, Map<String, Measures>> measures) {
        Map<Key, Measures> repetition = new LinkedHashMap<>();
        windows.forEach(window -> measures.apply(refreshes, window).forEach((service, measured) ->
                repetition.merge(new Key(configuration, window.phase(), service), measured, Measures::plus)));
        return repetition;
    }

    private static Map<String, Measures> measuresByService(List<List<ContainerStats>> refreshes, PhaseWindow window) {
        return refreshes.stream()
                .map(refresh -> inside(refresh, window).stream()
                        .collect(Collectors.groupingBy(ResourceUsage::service, TreeMap::new, Collectors.toList())))
                .flatMap(replicasByService -> replicasByService.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, replicas -> Measures.ofRefresh(replicas.getValue()), Measures::plus, TreeMap::new));
    }

    private static Map<String, Measures> measuresOfStack(List<List<ContainerStats>> refreshes, PhaseWindow window) {
        return refreshes.stream().map(refresh -> inside(refresh, window)).filter(containers -> !containers.isEmpty())
                .map(Measures::ofRefresh).reduce(Measures::plus).map(stack -> Map.of(STACK, stack)).orElse(Map.of());
    }

    private static List<ContainerStats> inside(List<ContainerStats> refresh, PhaseWindow window) {
        return refresh.stream().filter(sample -> window.contains(sample.at()) && ComposeReplica.of(sample.container()).isPresent()).toList();
    }
    private static String service(ContainerStats sample) {
        return ComposeReplica.of(sample.container()).orElseThrow().service();
    }

    private static List<List<ContainerStats>> refreshes(List<ContainerStats> samples) {
        List<List<ContainerStats>> refreshes = new ArrayList<>();
        Set<String> containersInRefresh = new HashSet<>();
        for (ContainerStats sample : samples) {
            if (refreshes.isEmpty() || !containersInRefresh.add(sample.container())) {
                refreshes.add(new ArrayList<>());
                containersInRefresh.clear();
                containersInRefresh.add(sample.container());
            }
            refreshes.getLast().add(sample);
        }
        return refreshes;
    }
}
