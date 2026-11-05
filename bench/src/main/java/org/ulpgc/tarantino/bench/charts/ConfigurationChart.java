package org.ulpgc.tarantino.bench.charts;

import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.ulpgc.tarantino.bench.run.TrialName;
import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * A measure of each configuration of a run with trials (SPEC §9): the mean over its repetitions with its 95 % CI, with
 * one bar per series.
 */
final class ConfigurationChart {

    private static final String X_AXIS = "Configuration";
    private static final int LABEL_ROTATION_DEGREES = 20;

    /** What to read from {@code T} and the name it has in the legend. */
    record Series<T>(String name, ToDoubleFunction<T> value) {
    }

    private ConfigurationChart() {
    }

    static <T> CategoryChart of(String title, String yAxis, Map<String, T> measuresByTrial, List<Series<T>> series) {
        Map<String, List<T>> repetitionsByConfiguration = measuresByTrial.entrySet().stream()
                .collect(Collectors.groupingBy(trial -> TrialName.configuration(trial.getKey()), LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        CategoryChart chart = new CategoryChartBuilder().width(RunChart.WIDTH).height(RunChart.HEIGHT)
                .title(title).xAxisTitle(X_AXIS).yAxisTitle(yAxis).build();
        chart.getStyler().setYAxisMin(0.0);
        chart.getStyler().setXAxisLabelRotation(LABEL_ROTATION_DEGREES);
        series.forEach(one -> addSeries(chart, one, repetitionsByConfiguration));
        return chart;
    }

    private static <T> void addSeries(CategoryChart chart, Series<T> series, Map<String, List<T>> repetitionsByConfiguration) {
        List<SummaryStats.Metrics> metrics = repetitionsByConfiguration.values().stream()
                .map(repetitions -> SummaryStats.calculate(repetitions.stream().map(series.value()::applyAsDouble).toList()))
                .toList();
        chart.addSeries(series.name(), List.copyOf(repetitionsByConfiguration.keySet()),
                metrics.stream().map(SummaryStats.Metrics::mean).toList(),
                metrics.stream().map(SummaryStats.Metrics::ci95).toList());
    }
}
