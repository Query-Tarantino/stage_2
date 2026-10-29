package org.ulpgc.tarantino.bench.charts;

import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.ulpgc.tarantino.bench.coordination.CoordinationTool;
import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The coordination cost of a run (SPEC §9): the mean queue wait and work of each phase over its published books, from
 * {@code coordination.csv}, with the 95 % CI as error bars.
 */
final class CoordinationChart {

    static final String NAME = "coordination";

    private static final String SEPARATOR = ",";
    private static final String TITLE = "Queue wait and work per phase";
    private static final String X_AXIS = "Phase";
    private static final String Y_AXIS = "Mean per book (ms)";
    private static final String SERIES = "mean";

    private CoordinationChart() {
    }

    /** The metrics of each phase column of {@code coordination.csv}, given as its lines with the header first. */
    static Map<String, SummaryStats.Metrics> phases(List<String> csv) {
        List<String> header = List.of(csv.getFirst().split(SEPARATOR));
        List<String[]> rows = csv.stream().skip(1).map(row -> row.split(SEPARATOR)).toList();
        return CoordinationTool.PHASE_COLUMNS.stream().collect(Collectors.toMap(Function.identity(),
                phase -> SummaryStats.calculate(rows.stream().map(row -> Long.parseLong(row[header.indexOf(phase)])).toList()),
                (first, second) -> first, LinkedHashMap::new));
    }

    static CategoryChart of(Map<String, SummaryStats.Metrics> phases) {
        CategoryChart chart = new CategoryChartBuilder().width(RunChart.WIDTH).height(RunChart.HEIGHT)
                .title(TITLE).xAxisTitle(X_AXIS).yAxisTitle(Y_AXIS).build();
        chart.getStyler().setLegendVisible(false);
        chart.addSeries(SERIES, List.copyOf(phases.keySet()),
                phases.values().stream().map(SummaryStats.Metrics::mean).toList(),
                phases.values().stream().map(SummaryStats.Metrics::ci95).toList());
        return chart;
    }
}
