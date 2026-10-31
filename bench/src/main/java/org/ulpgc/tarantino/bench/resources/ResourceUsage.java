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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The CPU and memory of each Compose service in each phase of each configuration of a run with trials (SPEC §9), from
 * the {@code stats.csv} samples inside the phase windows of every repetition. A refresh of {@code docker stats} lists
 * each container once, so a service's total CPU in a refresh is the sum over its replicas.
 */
public final class ResourceUsage {

    private static final double BYTES_PER_MIB = 1 << 20;

    private ResourceUsage() {
    }

    public record Key(String configuration, String phase, String service) {
    }

    public record Measures(List<Double> cpuPerReplica, List<Double> cpuTotal, List<Double> memoryPerReplicaMib) {

        static Measures ofRefresh(List<ContainerStats> replicas) {
            return new Measures(replicas.stream().map(ContainerStats::cpuPercent).toList(),
                    List.of(replicas.stream().mapToDouble(ContainerStats::cpuPercent).sum()),
                    replicas.stream().map(replica -> replica.memoryBytes() / BYTES_PER_MIB).toList());
        }

        Measures plus(Measures other) {
            return new Measures(concat(cpuPerReplica, other.cpuPerReplica), concat(cpuTotal, other.cpuTotal),
                    concat(memoryPerReplicaMib, other.memoryPerReplicaMib));
        }

        private static List<Double> concat(List<Double> first, List<Double> second) {
            return Stream.concat(first.stream(), second.stream()).toList();
        }
    }

    /** By configuration in trial order, then phase, then service by name; samples of other containers are left out. */
    public static Map<Key, Measures> of(List<ContainerStats> samples, Map<String, List<PhaseWindow>> windowsByTrial) {
        List<List<ContainerStats>> refreshes = refreshes(samples);
        Map<Key, Measures> usage = new LinkedHashMap<>();
        windowsByTrial.forEach((trial, windows) -> windows.forEach(window -> measuresByService(refreshes, window).forEach((service, measures) ->
                usage.merge(new Key(TrialName.configuration(trial), window.phase(), service), measures, Measures::plus))));
        return usage;
    }

    private static Map<String, Measures> measuresByService(List<List<ContainerStats>> refreshes, PhaseWindow window) {
        return refreshes.stream()
                .map(refresh -> refresh.stream()
                        .filter(sample -> window.contains(sample.at()) && ComposeReplica.of(sample.container()).isPresent())
                        .collect(Collectors.groupingBy(ResourceUsage::service, TreeMap::new, Collectors.toList())))
                .flatMap(replicasByService -> replicasByService.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, replicas -> Measures.ofRefresh(replicas.getValue()), Measures::plus, TreeMap::new));
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
