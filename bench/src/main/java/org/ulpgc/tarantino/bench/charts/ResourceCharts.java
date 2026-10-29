package org.ulpgc.tarantino.bench.charts;

import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.ulpgc.tarantino.bench.containers.ContainerStats;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/** The CPU and the memory of each container over a run, from {@code stats.csv} (SPEC §9), one line per container. */
final class ResourceCharts {

    static final String CPU = "cpu";
    static final String MEMORY = "memory";

    private static final double BYTES_PER_MIB = 1 << 20;
    private static final double MILLIS_PER_SECOND = 1_000;
    private static final String X_AXIS = "Seconds since the first sample";

    private ResourceCharts() {
    }

    /** The samples of {@code stats.csv}, given as its lines with the header first. */
    static List<ContainerStats> samples(List<String> csv) {
        return csv.stream().skip(1).map(ContainerStats::fromCsvRow).toList();
    }

    static XYChart cpu(List<ContainerStats> samples) {
        return chart("CPU per container", "CPU (%)", samples, ContainerStats::cpuPercent);
    }

    static XYChart memory(List<ContainerStats> samples) {
        return chart("Memory per container", "Memory (MiB)", samples, sample -> sample.memoryBytes() / BYTES_PER_MIB);
    }

    private static XYChart chart(String title, String yAxis, List<ContainerStats> samples, ToDoubleFunction<ContainerStats> value) {
        Instant firstSample = samples.stream().map(ContainerStats::at).min(Comparator.naturalOrder()).orElse(Instant.EPOCH);
        XYChart chart = new XYChartBuilder().width(RunChart.WIDTH).height(RunChart.HEIGHT)
                .title(title).xAxisTitle(X_AXIS).yAxisTitle(yAxis).build();
        chart.getStyler().setMarkerSize(0);
        samples.stream()
                .collect(Collectors.groupingBy(ContainerStats::container, TreeMap::new, Collectors.toList()))
                .forEach((container, series) -> chart.addSeries(container,
                        series.stream().mapToDouble(sample -> secondsBetween(firstSample, sample.at())).toArray(),
                        series.stream().mapToDouble(value).toArray()));
        return chart;
    }

    private static double secondsBetween(Instant start, Instant end) {
        return Duration.between(start, end).toMillis() / MILLIS_PER_SECOND;
    }
}
