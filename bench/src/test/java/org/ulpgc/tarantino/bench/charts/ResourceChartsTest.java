package org.ulpgc.tarantino.bench.charts;

import org.junit.jupiter.api.Test;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYSeries;

import java.util.List;
import java.util.stream.DoubleStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceChartsTest {

    private static final List<String> STATS = List.of(
            "instant,container,cpu_percent,memory_bytes",
            "2026-10-21T10:00:00Z,stage_2-indexer-1,12.50,268435456",
            "2026-10-21T10:00:00.500Z,stage_2-crawler-1,3.00,134217728",
            "2026-10-21T10:00:01Z,stage_2-indexer-1,112.47,536870912");

    @Test
    void drawsOneCpuLinePerContainerAgainstTheSecondsSinceTheFirstSample() {
        XYChart chart = ResourceCharts.cpu(ResourceCharts.samples(STATS));

        assertEquals(List.of("stage_2-crawler-1", "stage_2-indexer-1"), List.copyOf(chart.getSeriesMap().keySet()));
        XYSeries indexer = chart.getSeriesMap().get("stage_2-indexer-1");
        assertArrayEquals(new double[] {0, 1}, indexer.getXData());
        assertArrayEquals(new double[] {12.5, 112.47}, indexer.getYData());
        assertArrayEquals(new double[] {0.5}, chart.getSeriesMap().get("stage_2-crawler-1").getXData());
    }

    @Test
    void coloursEachServiceTheSameInEveryRunAndTellsItsReplicasApartByLineStyle() {
        XYChart chart = ResourceCharts.memory(ResourceCharts.samples(List.of(
                "instant,container,cpu_percent,memory_bytes",
                "2026-10-21T10:00:00Z,stage_2-crawler-1,3.00,134217728",
                "2026-10-21T10:00:00Z,stage_2-crawler-2,3.00,134217728",
                "2026-10-21T10:00:00Z,stage_2-nginx-1,1.00,8388608",
                "2026-10-21T10:00:00Z,stage_2-bench-run-e1218bd40d63,1.00,8388608")));

        XYSeries firstCrawler = chart.getSeriesMap().get("stage_2-crawler-1");
        XYSeries secondCrawler = chart.getSeriesMap().get("stage_2-crawler-2");
        assertEquals(ResourceCharts.SERVICE_COLORS.get("crawler"), firstCrawler.getLineColor());
        assertEquals(ResourceCharts.SERVICE_COLORS.get("crawler"), secondCrawler.getLineColor());
        assertEquals(ResourceCharts.REPLICA_LINES.get(0), firstCrawler.getLineStyle());
        assertEquals(ResourceCharts.REPLICA_LINES.get(1), secondCrawler.getLineStyle());
        assertEquals(ResourceCharts.SERVICE_COLORS.get("nginx"), chart.getSeriesMap().get("stage_2-nginx-1").getLineColor());
        assertEquals(ResourceCharts.OTHER_CONTAINER, chart.getSeriesMap().get("stage_2-bench-run-e1218bd40d63").getLineColor());
    }

    @Test
    void drawsTheMemoryInMebibytes() {
        XYChart chart = ResourceCharts.memory(ResourceCharts.samples(STATS));

        assertArrayEquals(DoubleStream.of(256, 512).toArray(), chart.getSeriesMap().get("stage_2-indexer-1").getYData());
    }
}
