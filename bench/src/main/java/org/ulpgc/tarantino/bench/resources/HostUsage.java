package org.ulpgc.tarantino.bench.resources;

import org.ulpgc.tarantino.bench.containers.HostStats;
import org.ulpgc.tarantino.bench.run.TrialName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The CPU and memory of the whole host in each phase of each configuration of a run with trials (SPEC §9), from the
 * {@code host.csv} samples inside the phase windows, kept apart per repetition.
 */
public final class HostUsage {

    /** The service of the keys that measure the whole host. */
    public static final String HOST = "host";

    private HostUsage() {
    }

    public record Measures(List<Double> cpuPercent, List<Double> memoryUsedMib) {
    }

    /** The host's measures, one per repetition in trial order; keys by configuration and phase, with {@link #HOST} as service. */
    public static Map<ResourceUsage.Key, List<Measures>> perRepetition(List<HostStats> samples, Map<String, List<PhaseWindow>> windowsByTrial) {
        Map<ResourceUsage.Key, List<Measures>> usage = new LinkedHashMap<>();
        windowsByTrial.forEach((trial, windows) -> windows.forEach(window -> inside(samples, window).ifPresent(measures ->
                usage.computeIfAbsent(new ResourceUsage.Key(TrialName.configuration(trial), window.phase(), HOST),
                        key -> new ArrayList<>()).add(measures))));
        return usage;
    }

    private static Optional<Measures> inside(List<HostStats> samples, PhaseWindow window) {
        List<HostStats> inside = samples.stream().filter(sample -> window.contains(sample.at())).toList();
        if (inside.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Measures(inside.stream().map(HostStats::cpuPercent).toList(),
                inside.stream().map(sample -> sample.memoryUsedBytes() / ResourceUsage.BYTES_PER_MIB).toList()));
    }
}
