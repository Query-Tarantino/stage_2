package org.ulpgc.tarantino.bench.charts;

import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.ulpgc.tarantino.bench.run.TrialName;
import org.ulpgc.tarantino.bench.stats.SummaryStats;
import org.ulpgc.tarantino.bench.throughput.ThroughputWindow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The throughput of each configuration of a run with trials (SPEC §9): the mean over its repetitions with its 95 % CI,
 * as {@code summary.csv} holds it, with one bar per window.
 */
final class ThroughputChart {

    private static final String X_AXIS = "Configuration";
    private static final String Y_AXIS = "Mean throughput (books/s)";
    private static final int LABEL_ROTATION_DEGREES = 20;

    /** A window of {@code T} and the name it has in the legend. */
    record Series<T>(String name, Function<T, ThroughputWindow> window) {
    }

    private ThroughputChart() {
    }

    static <T> CategoryChart of(String title, Map<String, T> throughputsByTrial, List<Series<T>> series) {
        Map<String, List<T>> repetitionsByConfiguration = throughputsByTrial.entrySet().stream()
                .collect(Collectors.groupingBy(trial -> TrialName.configuration(trial.getKey()), LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        CategoryChart chart = new CategoryChartBuilder().width(RunChart.WIDTH).height(RunChart.HEIGHT)
                .title(title).xAxisTitle(X_AXIS).yAxisTitle(Y_AXIS).build();
        chart.getStyler().setYAxisMin(0.0);
        chart.getStyler().setXAxisLabelRotation(LABEL_ROTATION_DEGREES);
        series.forEach(one -> addSeries(chart, one, repetitionsByConfiguration));
        return chart;
    }

    private static <T> void addSeries(CategoryChart chart, Series<T> series, Map<String, List<T>> repetitionsByConfiguration) {
        List<SummaryStats.Metrics> metrics = repetitionsByConfiguration.values().stream()
                .map(repetitions -> SummaryStats.calculate(repetitions.stream().map(series.window())
                        .map(ThroughputWindow::booksPerSecond).toList()))
                .toList();
        chart.addSeries(series.name(), List.copyOf(repetitionsByConfiguration.keySet()),
                metrics.stream().map(SummaryStats.Metrics::mean).toList(),
                metrics.stream().map(SummaryStats.Metrics::ci95).toList());
    }
}
