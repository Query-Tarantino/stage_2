package org.ulpgc.tarantino.bench.charts;

import org.knowm.xchart.CategoryChart;
import org.ulpgc.tarantino.bench.throughput.ThroughputWindow;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The indexing throughput of each configuration of an {@code index-throughput} run (SPEC §9): the mean over its
 * repetitions with its 95 % CI, as {@code bench index-throughput} summarises it, over the whole window.
 */
final class IndexChart {

    static final String NAME = "index-throughput";
    static final String WHOLE = "Whole window";

    private static final String TITLE = "Indexing throughput per configuration";

    private IndexChart() {
    }

    static CategoryChart of(Map<String, ThroughputWindow> throughputsByTrial) {
        return ThroughputChart.of(TITLE, throughputsByTrial, List.of(new ThroughputChart.Series<>(WHOLE, Function.identity())));
    }
}
