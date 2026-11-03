package org.ulpgc.tarantino.bench.charts;

import org.junit.jupiter.api.Test;
import org.knowm.xchart.CategoryChart;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LatencyChartTest {

    private static final String HEADER = "metric,configuration,mean,std_dev,ci_95,p50,p95,p99,unit";

    @Test
    void readsOnlyTheTrialRowsOfTheLatencyInFactorOrder() {
        Map<String, LatencyChart.TrialLatency> trials = LatencyChart.trials(List.of(HEADER,
                row("search_latency", "clients-32-threads-32-replicas-1-rep-1", 6000, 10000),
                row("search_latency", "clients-8-threads-32-replicas-1-rep-1", 2000, 4000),
                row("search_latency", "clients=8,threads=32,replicas=1", 9000, 9000),
                row("ingest_throughput", "replicas-1-workers-8-rep-1", 300, 300)));

        assertEquals(List.of("clients-8-threads-32-replicas-1-rep-1", "clients-32-threads-32-replicas-1-rep-1"), List.copyOf(trials.keySet()));
        assertEquals(new LatencyChart.TrialLatency(2.0, 4.0), trials.get("clients-8-threads-32-replicas-1-rep-1"));
    }

    @Test
    void hasNoTrialsInASummaryWithoutLatencies() {
        assertEquals(Map.of(), LatencyChart.trials(List.of(HEADER, row("ingest_throughput", "replicas-1-workers-8-rep-1", 300, 300))));
    }

    @Test
    void drawsTheMeanOfEachConfigurationOverItsRepetitionsInMilliseconds() {
        CategoryChart chart = LatencyChart.of(LatencyChart.trials(List.of(HEADER,
                row("search_latency", "clients-8-threads-32-replicas-1-rep-1", 2000, 4000),
                row("search_latency", "clients-8-threads-32-replicas-1-rep-2", 4000, 8000),
                row("search_latency", "clients-32-threads-32-replicas-1-rep-1", 6000, 10000))));

        assertEquals(List.of(LatencyChart.MEAN, LatencyChart.P95), List.copyOf(chart.getSeriesMap().keySet()));
        assertEquals(List.of("clients=8,threads=32,replicas=1", "clients=32,threads=32,replicas=1"), chart.getSeriesMap().get(LatencyChart.MEAN).getXData());
        assertEquals(List.of(3.0, 6.0), chart.getSeriesMap().get(LatencyChart.MEAN).getYData());
        assertEquals(List.of(6.0, 10.0), chart.getSeriesMap().get(LatencyChart.P95).getYData());
    }

    private static String row(String metric, String configuration, int mean, int p95) {
        return metric + ",\"" + configuration + "\"," + mean + ".00,1.00,1.00," + mean + ".00," + p95 + ".00," + p95 + ".00,"
                + (metric.equals("search_latency") ? "us" : "books/s");
    }
}
