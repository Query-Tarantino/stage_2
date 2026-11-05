package org.ulpgc.tarantino.bench.charts;

import org.knowm.xchart.CategoryChart;
import org.ulpgc.tarantino.bench.ingest.IngestThroughput;

import java.util.List;
import java.util.Map;

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
    private static final String Y_AXIS = "Mean throughput (books/s)";

    private IngestChart() {
    }

    static CategoryChart of(Map<String, IngestThroughput> throughputsByTrial) {
        return ConfigurationChart.of(TITLE, Y_AXIS, throughputsByTrial, List.of(
                new ConfigurationChart.Series<>(WHOLE, throughput -> throughput.whole().booksPerSecond()),
                new ConfigurationChart.Series<>(STEADY, throughput -> throughput.steady().booksPerSecond())));
    }
}
