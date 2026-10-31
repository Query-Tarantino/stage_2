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
        Map<String, Map<String, SummaryStats.Metrics>> phases = CoordinationChart.phases(List.of(
                "book_id,download_queue_wait_ms,download_ms,index_queue_wait_ms,indexing_ms,time_to_publication_ms",
                "84,100,1000,40,3000,4140",
                "1342,300,2000,60,5000,7360"));

        assertEquals(List.of(CoordinationChart.ALL_BOOKS), List.copyOf(phases.keySet()));
        assertEquals(CoordinationTool.PHASE_COLUMNS, List.copyOf(phases.get(CoordinationChart.ALL_BOOKS).keySet()));
        assertEquals(List.of(200.0, 1500.0, 50.0, 4000.0),
                phases.get(CoordinationChart.ALL_BOOKS).values().stream().map(SummaryStats.Metrics::mean).toList());
    }

    @Test
    void drawsOneSeriesPerConfigurationOverTheBooksOfAllItsRepetitions() {
        Map<String, Map<String, SummaryStats.Metrics>> phases = CoordinationChart.phases(List.of(
                "trial,book_id,download_queue_wait_ms,download_ms,index_queue_wait_ms,indexing_ms,time_to_publication_ms",
                "replicas-1-rep-1,84,100,1000,40,3000,4140",
                "replicas-1-rep-2,84,300,1000,40,3000,4340",
                "replicas-2-rep-1,84,50,1000,40,3000,4090"));

        assertEquals(List.of("replicas=1", "replicas=2"), List.copyOf(CoordinationChart.of(phases).getSeriesMap().keySet()));
        assertEquals(200.0, phases.get("replicas=1").get("download_queue_wait_ms").mean());
        assertEquals(50.0, phases.get("replicas=2").get("download_queue_wait_ms").mean());
    }

    @Test
    void drawsOneBarPerPhase() {
        Map<String, Map<String, SummaryStats.Metrics>> phases = CoordinationChart.phases(List.of(
                "book_id,download_queue_wait_ms,download_ms,index_queue_wait_ms,indexing_ms,time_to_publication_ms",
                "84,100,1000,40,3000,4140"));

        assertEquals(CoordinationTool.PHASE_COLUMNS, CoordinationChart.of(phases).getSeriesMap().values().iterator().next().getXData());
    }
}
