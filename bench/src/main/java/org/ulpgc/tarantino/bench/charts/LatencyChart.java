package org.ulpgc.tarantino.bench.charts;

import org.knowm.xchart.CategoryChart;
import org.ulpgc.tarantino.bench.run.TrialName;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The latency of {@code /search} in each configuration of a {@code search-latency} run (SPEC §9), from the rows of its
 * trials in {@code summary.csv}: the mean over the repetitions of each trial's mean and of each trial's p95, with their
 * 95 % CI, so the error bars show how much the repetitions differ and not how many requests there were.
 */
final class LatencyChart {

    static final String NAME = "search-latency";
    static final String MEAN = "Mean";
    static final String P95 = "p95";

    private static final String TITLE = "Search latency per configuration";
    private static final String Y_AXIS = "Latency, mean over repetitions (ms)";
    private static final double MICROSECONDS_PER_MILLISECOND = 1000.0;
    private static final Pattern LATENCY_ROW = Pattern.compile(
            "search_latency,\"(?<configuration>[^\"]+)\",(?<mean>[^,]+),[^,]+,[^,]+,[^,]+,(?<p95>[^,]+),[^,]+,us");

    /** The latency of one trial, in milliseconds. */
    record TrialLatency(double mean, double p95) {
    }

    private LatencyChart() {
    }

    /** The latency of each trial by trial name, in {@link TrialName#ORDER}; rows of other metrics or of a whole configuration do not count. */
    static Map<String, TrialLatency> trials(List<String> summaryRows) {
        return summaryRows.stream()
                .map(LATENCY_ROW::matcher)
                .filter(Matcher::matches)
                .filter(row -> TrialName.isValid(row.group("configuration")))
                .sorted((first, second) -> TrialName.ORDER.compare(first.group("configuration"), second.group("configuration")))
                .collect(Collectors.toMap(row -> row.group("configuration"), LatencyChart::latency, (first, second) -> first, LinkedHashMap::new));
    }

    static CategoryChart of(Map<String, TrialLatency> latencyByTrial) {
        return ConfigurationChart.of(TITLE, Y_AXIS, latencyByTrial, List.of(
                new ConfigurationChart.Series<>(MEAN, TrialLatency::mean),
                new ConfigurationChart.Series<>(P95, TrialLatency::p95)));
    }

    private static TrialLatency latency(Matcher row) {
        return new TrialLatency(milliseconds(row.group("mean")), milliseconds(row.group("p95")));
    }

    private static double milliseconds(String microseconds) {
        return Double.parseDouble(microseconds) / MICROSECONDS_PER_MILLISECOND;
    }
}
