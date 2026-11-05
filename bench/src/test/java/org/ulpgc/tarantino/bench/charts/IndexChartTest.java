package org.ulpgc.tarantino.bench.charts;

import org.junit.jupiter.api.Test;
import org.knowm.xchart.CategoryChart;
import org.ulpgc.tarantino.bench.throughput.ThroughputWindow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndexChartTest {

    @Test
    void drawsTheMeanOfEachConfigurationOverItsRepetitionsForTheWholeWindow() {
        Map<String, ThroughputWindow> throughputs = new LinkedHashMap<>();
        throughputs.put("batch-10-replicas-1-rep-1", new ThroughputWindow(100, 1.0));
        throughputs.put("batch-10-replicas-1-rep-2", new ThroughputWindow(140, 1.0));
        throughputs.put("batch-100-replicas-2-rep-1", new ThroughputWindow(200, 1.0));

        CategoryChart chart = IndexChart.of(throughputs);

        assertEquals(List.of(IndexChart.WHOLE), List.copyOf(chart.getSeriesMap().keySet()));
        assertEquals(List.of("batch=10,replicas=1", "batch=100,replicas=2"), chart.getSeriesMap().get(IndexChart.WHOLE).getXData());
        assertEquals(List.of(120.0, 200.0), chart.getSeriesMap().get(IndexChart.WHOLE).getYData());
    }
}
