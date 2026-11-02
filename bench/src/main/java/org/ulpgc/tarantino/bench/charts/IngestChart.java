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

    private IngestChart() {
    }

    static CategoryChart of(Map<String, IngestThroughput> throughputsByTrial) {
        return ThroughputChart.of(TITLE, throughputsByTrial, List.of(
                new ThroughputChart.Series<>(WHOLE, IngestThroughput::whole),
                new ThroughputChart.Series<>(STEADY, IngestThroughput::steady)));
    }
}
