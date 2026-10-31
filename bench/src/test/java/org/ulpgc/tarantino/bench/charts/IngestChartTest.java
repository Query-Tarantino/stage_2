package org.ulpgc.tarantino.bench.charts;

import org.junit.jupiter.api.Test;
import org.knowm.xchart.CategoryChart;
import org.ulpgc.tarantino.bench.ingest.IngestThroughput;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IngestChartTest {

    @Test
    void drawsTheMeanOfEachConfigurationOverItsRepetitionsForBothWindows() {
        Map<String, IngestThroughput> throughputs = new LinkedHashMap<>();
        throughputs.put("replicas-1-workers-4-rep-1", throughput(100, 120));
        throughputs.put("replicas-1-workers-4-rep-2", throughput(140, 160));
        throughputs.put("replicas-2-workers-8-rep-1", throughput(200, 210));

        CategoryChart chart = IngestChart.of(throughputs);

        assertEquals(List.of(IngestChart.WHOLE, IngestChart.STEADY), List.copyOf(chart.getSeriesMap().keySet()));
        assertEquals(List.of("replicas=1,workers=4", "replicas=2,workers=8"), chart.getSeriesMap().get(IngestChart.WHOLE).getXData());
        assertEquals(List.of(120.0, 200.0), chart.getSeriesMap().get(IngestChart.WHOLE).getYData());
        assertEquals(List.of(140.0, 210.0), chart.getSeriesMap().get(IngestChart.STEADY).getYData());
    }

    private static IngestThroughput throughput(int wholeBooksPerSecond, int steadyBooksPerSecond) {
        return new IngestThroughput(new IngestThroughput.Window(wholeBooksPerSecond, 1.0), new IngestThroughput.Window(steadyBooksPerSecond, 1.0));
    }
}
