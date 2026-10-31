package org.ulpgc.tarantino.bench.charts;

import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.ulpgc.tarantino.bench.ingest.IngestThroughput;
import org.ulpgc.tarantino.bench.run.TrialName;
import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The download throughput of each configuration of an {@code ingest-throughput} run (SPEC §9): the mean over its
 * repetitions with its 95 % CI, as {@code bench ingest-throughput} summarises it, over the whole window and over the
 * steady one.
 */
final class IngestChart {

    static final String NAME = "ingest-throughput";
    static final String WHOLE = "Whole window";
    static final String STEADY = "10-90 % of the books";

    private static final String TITLE = "Download throughput per configuration";
    private static final String X_AXIS = "Configuration";
    private static final String Y_AXIS = "Mean throughput (books/s)";
    private static final int LABEL_ROTATION_DEGREES = 20;

    private IngestChart() {
    }

    static CategoryChart of(Map<String, IngestThroughput> throughputsByTrial) {
        Map<String, List<IngestThroughput>> repetitionsByConfiguration = throughputsByTrial.entrySet().stream()
                .collect(Collectors.groupingBy(trial -> TrialName.configuration(trial.getKey()), LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        CategoryChart chart = new CategoryChartBuilder().width(RunChart.WIDTH).height(RunChart.HEIGHT)
                .title(TITLE).xAxisTitle(X_AXIS).yAxisTitle(Y_AXIS).build();
        chart.getStyler().setYAxisMin(0.0);
        chart.getStyler().setXAxisLabelRotation(LABEL_ROTATION_DEGREES);
        addSeries(chart, WHOLE, repetitionsByConfiguration, IngestThroughput::whole);
        addSeries(chart, STEADY, repetitionsByConfiguration, IngestThroughput::steady);
        return chart;
    }

    private static void addSeries(CategoryChart chart, String series, Map<String, List<IngestThroughput>> repetitionsByConfiguration,
                                  Function<IngestThroughput, IngestThroughput.Window> window) {
        List<SummaryStats.Metrics> metrics = repetitionsByConfiguration.values().stream()
                .map(repetitions -> SummaryStats.calculate(repetitions.stream().map(window)
                        .map(measured -> Math.round(measured.booksPerSecond())).toList()))
                .toList();
        chart.addSeries(series, List.copyOf(repetitionsByConfiguration.keySet()),
                metrics.stream().map(SummaryStats.Metrics::mean).toList(),
                metrics.stream().map(SummaryStats.Metrics::ci95).toList());
    }
}
