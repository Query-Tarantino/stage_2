package org.ulpgc.tarantino.bench.charts;

import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.ulpgc.tarantino.bench.coordination.CoordinationTool;
import org.ulpgc.tarantino.bench.run.TrialName;
import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The coordination cost of a run (SPEC §9): the mean queue wait and work of each phase over its published books, from
 * {@code coordination.csv}, with the 95 % CI as error bars; in a run with trials, one series per configuration over the
 * books of all its repetitions.
 */
final class CoordinationChart {

    static final String NAME = "coordination";
    static final String ALL_BOOKS = "mean";

    private static final String SEPARATOR = ",";
    private static final String TRIAL_COLUMN = "trial";
    private static final String TITLE = "Queue wait and work per phase";
    private static final String X_AXIS = "Phase";
    private static final String Y_AXIS = "Mean per book (ms)";

    private CoordinationChart() {
    }

    /** The metrics of each phase column of {@code coordination.csv} per series, given as its lines with the header first. */
    static Map<String, Map<String, SummaryStats.Metrics>> phases(List<String> csv) {
        List<String> header = List.of(csv.getFirst().split(SEPARATOR));
        int trial = header.indexOf(TRIAL_COLUMN);
        Map<String, List<String[]>> rowsBySeries = csv.stream().skip(1).map(row -> row.split(SEPARATOR))
                .collect(Collectors.groupingBy(row -> trial < 0 ? ALL_BOOKS : TrialName.configuration(row[trial]),
                        LinkedHashMap::new, Collectors.toList()));
        if (rowsBySeries.isEmpty()) {
            rowsBySeries.put(ALL_BOOKS, List.of());
        }
        return rowsBySeries.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                series -> metricsPerPhase(header, series.getValue()), (first, second) -> first, LinkedHashMap::new));
    }

    static CategoryChart of(Map<String, Map<String, SummaryStats.Metrics>> phasesBySeries) {
        CategoryChart chart = new CategoryChartBuilder().width(RunChart.WIDTH).height(RunChart.HEIGHT)
                .title(TITLE).xAxisTitle(X_AXIS).yAxisTitle(Y_AXIS).build();
        chart.getStyler().setLegendVisible(phasesBySeries.size() > 1);
        phasesBySeries.forEach((series, phases) -> chart.addSeries(series, List.copyOf(phases.keySet()),
                phases.values().stream().map(SummaryStats.Metrics::mean).toList(),
                phases.values().stream().map(SummaryStats.Metrics::ci95).toList()));
        return chart;
    }

    private static Map<String, SummaryStats.Metrics> metricsPerPhase(List<String> header, List<String[]> rows) {
        return CoordinationTool.PHASE_COLUMNS.stream().collect(Collectors.toMap(Function.identity(),
                phase -> SummaryStats.calculate(rows.stream().map(row -> Long.parseLong(row[header.indexOf(phase)])).toList()),
                (first, second) -> first, LinkedHashMap::new));
    }
}
