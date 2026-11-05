package org.ulpgc.tarantino.bench.charts;

import org.apache.commons.math3.distribution.TDistribution;
import org.junit.jupiter.api.Test;
import org.knowm.xchart.CategoryChart;
import org.ulpgc.tarantino.bench.resources.PhaseWindow;
import org.ulpgc.tarantino.bench.resources.ResourceUsage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhaseResourceChartsTest {

    @Test
    void drawsOneSeriesPerConfigurationWithABarPerServiceAndZeroWhereTheServiceDidNotRun() {
        Map<ResourceUsage.Key, List<ResourceUsage.Measures>> usage = new LinkedHashMap<>();
        usage.put(key("replicas=1", PhaseWindow.INGESTION, "crawler"), measures(List.of(40.0, 60.0)));
        usage.put(key("replicas=1", PhaseWindow.INDEXING, "indexer"), measures(List.of(90.0)));
        usage.put(key("replicas=2", PhaseWindow.INGESTION, "crawler"), measures(List.of(100.0)));
        usage.put(key("replicas=2", PhaseWindow.INGESTION, "mongo"), measures(List.of(30.0)));

        CategoryChart ingestion = PhaseResourceCharts.cpu(usage, PhaseWindow.INGESTION);

        assertEquals(List.of(PhaseWindow.INGESTION, PhaseWindow.INDEXING), PhaseResourceCharts.phases(usage));
        assertEquals(List.of("replicas=1", "replicas=2"), List.copyOf(ingestion.getSeriesMap().keySet()));
        assertEquals(List.of("crawler", "mongo"), ingestion.getSeriesMap().get("replicas=1").getXData());
        assertEquals(List.of(50.0, 0.0), ingestion.getSeriesMap().get("replicas=1").getYData());
        assertEquals(List.of(100.0, 30.0), ingestion.getSeriesMap().get("replicas=2").getYData());
    }

    @Test
    void drawsTheErrorBarsWithTheCiOverTheMeanOfEachRepetition() {
        Map<ResourceUsage.Key, List<ResourceUsage.Measures>> usage = Map.of(key("replicas=1", PhaseWindow.INGESTION, "crawler"),
                List.of(measures(List.of(10.0, 30.0)).getFirst(), measures(List.of(60.0)).getFirst()));

        CategoryChart ingestion = PhaseResourceCharts.cpu(usage, PhaseWindow.INGESTION);

        assertEquals(List.of(40.0), ingestion.getSeriesMap().get("replicas=1").getYData());
        assertEquals(new TDistribution(1).inverseCumulativeProbability(0.975) * Math.sqrt(800) / Math.sqrt(2),
                ingestion.getSeriesMap().get("replicas=1").getExtraValues().iterator().next().doubleValue(), 0.01);
    }

    private static ResourceUsage.Key key(String configuration, String phase, String service) {
        return new ResourceUsage.Key(configuration, phase, service);
    }

    private static List<ResourceUsage.Measures> measures(List<Double> cpuTotal) {
        return List.of(new ResourceUsage.Measures(cpuTotal, cpuTotal, cpuTotal, cpuTotal));
    }
}
