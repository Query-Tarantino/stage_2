package org.ulpgc.tarantino.bench.charts;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.bench.coordination.CoordinationTool;
import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoordinationChartTest {

    @Test
    void averagesEveryPhaseColumnOverThePublishedBooksInTheirOrder() {
        Map<String, SummaryStats.Metrics> phases = CoordinationChart.phases(List.of(
                "book_id,download_queue_wait_ms,download_ms,index_queue_wait_ms,indexing_ms,time_to_publication_ms",
                "84,100,1000,40,3000,4140",
                "1342,300,2000,60,5000,7360"));

        assertEquals(CoordinationTool.PHASE_COLUMNS, List.copyOf(phases.keySet()));
        assertEquals(List.of(200.0, 1500.0, 50.0, 4000.0), phases.values().stream().map(SummaryStats.Metrics::mean).toList());
    }

    @Test
    void drawsOneBarPerPhase() {
        Map<String, SummaryStats.Metrics> phases = CoordinationChart.phases(List.of(
                "book_id,download_queue_wait_ms,download_ms,index_queue_wait_ms,indexing_ms,time_to_publication_ms",
                "84,100,1000,40,3000,4140"));

        assertEquals(CoordinationTool.PHASE_COLUMNS, CoordinationChart.of(phases).getSeriesMap().values().iterator().next().getXData());
    }
}
