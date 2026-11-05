package org.ulpgc.tarantino.bench.charts;

import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.lines.SeriesLines;
import org.ulpgc.tarantino.bench.containers.ComposeReplica;
import org.ulpgc.tarantino.bench.containers.ContainerStats;

import java.awt.BasicStroke;
import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * The CPU and the memory of each container over a run, from {@code stats.csv} (SPEC §9), one line per container: each
 * Compose service keeps its colour in every run, from the Okabe-Ito palette, and its replicas differ by line style.
 */
final class ResourceCharts {

    static final String CPU = "cpu";
    static final String MEMORY = "memory";
    static final Map<String, Color> SERVICE_COLORS = Map.of(
            "control", new Color(0xCC79A7),
            "crawler", new Color(0xE69F00),
            "hazelcast", new Color(0x56B4E9),
            "indexer", new Color(0x0072B2),
            "mongo", new Color(0xD55E00),
            "nginx", Color.BLACK,
            "query", new Color(0x009E73));
    static final Color OTHER_CONTAINER = Color.GRAY;
    static final List<BasicStroke> REPLICA_LINES = List.of(SeriesLines.SOLID, SeriesLines.DASH_DASH, SeriesLines.DOT_DOT, SeriesLines.DASH_DOT);

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
                .forEach((container, series) -> style(chart.addSeries(container,
                        series.stream().mapToDouble(sample -> secondsBetween(firstSample, sample.at())).toArray(),
                        series.stream().mapToDouble(value).toArray()), container));
        return chart;
    }

    private static void style(XYSeries series, String container) {
        Optional<ComposeReplica> replica = ComposeReplica.of(container);
        series.setLineColor(replica.map(ResourceCharts::color).orElse(OTHER_CONTAINER));
        series.setLineStyle(replica.map(compose -> REPLICA_LINES.get((compose.replica() - 1) % REPLICA_LINES.size())).orElse(SeriesLines.SOLID));
    }

    private static Color color(ComposeReplica replica) {
        return SERVICE_COLORS.getOrDefault(replica.service(), OTHER_CONTAINER);
    }

    private static double secondsBetween(Instant start, Instant end) {
        return Duration.between(start, end).toMillis() / MILLIS_PER_SECOND;
    }
}
