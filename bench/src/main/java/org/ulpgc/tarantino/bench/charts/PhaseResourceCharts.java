package org.ulpgc.tarantino.bench.charts;

import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.ulpgc.tarantino.bench.resources.ResourceUsage;
import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The resources of each service during one phase of a {@code resource-usage} run (SPEC §9): one bar per service and
 * configuration, the mean over every repetition with its 95 % CI, as {@code bench resources} summarises them, and 0
 * where the service did not run in that phase.
 */
final class PhaseResourceCharts {

    static final String CPU_PREFIX = "cpu-";
    static final String MEMORY_PREFIX = "memory-";

    private static final String X_AXIS = "Service";

    private PhaseResourceCharts() {
    }

    static List<String> phases(Map<ResourceUsage.Key, ResourceUsage.Measures> usage) {
        return usage.keySet().stream().map(ResourceUsage.Key::phase).distinct().toList();
    }

    static CategoryChart cpu(Map<ResourceUsage.Key, ResourceUsage.Measures> usage, String phase) {
        return chart("Total CPU per service during " + phase, "Mean total CPU (%)", usage, phase, ResourceUsage.Measures::cpuTotal);
    }

    static CategoryChart memory(Map<ResourceUsage.Key, ResourceUsage.Measures> usage, String phase) {
        return chart("Memory per replica during " + phase, "Mean memory per replica (MiB)", usage, phase, ResourceUsage.Measures::memoryPerReplicaMib);
    }

    private static CategoryChart chart(String title, String yAxis, Map<ResourceUsage.Key, ResourceUsage.Measures> usage, String phase,
                                       Function<ResourceUsage.Measures, List<Double>> values) {
        List<ResourceUsage.Key> keys = usage.keySet().stream().filter(key -> key.phase().equals(phase)).toList();
        List<String> services = keys.stream().map(ResourceUsage.Key::service).distinct().sorted().toList();
        CategoryChart chart = new CategoryChartBuilder().width(RunChart.WIDTH).height(RunChart.HEIGHT)
                .title(title).xAxisTitle(X_AXIS).yAxisTitle(yAxis).build();
        chart.getStyler().setYAxisMin(0.0);
        keys.stream().map(ResourceUsage.Key::configuration).distinct().forEach(configuration -> {
            List<Optional<SummaryStats.Metrics>> metrics = services.stream()
                    .map(service -> Optional.ofNullable(usage.get(new ResourceUsage.Key(configuration, phase, service)))
                            .map(measures -> SummaryStats.calculate(values.apply(measures).stream().map(Math::round).toList())))
                    .toList();
            chart.addSeries(configuration, services,
                    metrics.stream().map(metric -> metric.map(SummaryStats.Metrics::mean).orElse(0.0)).toList(),
                    metrics.stream().map(metric -> metric.map(SummaryStats.Metrics::ci95).orElse(0.0)).toList());
        });
        return chart;
    }
}
