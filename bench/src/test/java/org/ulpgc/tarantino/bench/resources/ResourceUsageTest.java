package org.ulpgc.tarantino.bench.resources;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.bench.containers.ContainerStats;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceUsageTest {

    private static final long MIB = 1 << 20;
    private static final PhaseWindow INGESTION = new PhaseWindow(PhaseWindow.INGESTION, at("10:00:01Z"), at("10:00:02Z"));

    @Test
    void measuresEachServicePerReplicaAndAddsItsReplicasUpPerRefreshInsideTheWindow() {
        List<ContainerStats> samples = List.of(
                sample("10:00:00.0Z", "stage_2-crawler-1", 99, 99),
                sample("10:00:01.0Z", "stage_2-crawler-1", 10, 100),
                sample("10:00:01.1Z", "stage_2-crawler-2", 20, 200),
                sample("10:00:01.2Z", "stage_2-mongo-1", 5, 300),
                sample("10:00:01.3Z", "stage_2-bench-run-e1218bd40d63", 1, 1),
                sample("10:00:02.0Z", "stage_2-crawler-1", 30, 100),
                sample("10:00:02.0Z", "stage_2-crawler-2", 40, 200));

        Map<ResourceUsage.Key, ResourceUsage.Measures> usage = ResourceUsage.of(samples, Map.of("replicas-2-rep-1", List.of(INGESTION)));

        assertEquals(List.of(new ResourceUsage.Key("replicas=2", PhaseWindow.INGESTION, "crawler"),
                new ResourceUsage.Key("replicas=2", PhaseWindow.INGESTION, "mongo")), List.copyOf(usage.keySet()));
        ResourceUsage.Measures crawler = usage.get(new ResourceUsage.Key("replicas=2", PhaseWindow.INGESTION, "crawler"));
        assertEquals(List.of(10.0, 20.0, 30.0, 40.0), crawler.cpuPerReplica());
        assertEquals(List.of(30.0, 70.0), crawler.cpuTotal());
        assertEquals(List.of(100.0, 200.0, 100.0, 200.0), crawler.memoryPerReplicaMib());
    }

    @Test
    void poolsTheRepetitionsOfAConfiguration() {
        List<ContainerStats> samples = List.of(
                sample("10:00:01Z", "stage_2-crawler-1", 10, 100),
                sample("11:00:01Z", "stage_2-crawler-1", 30, 100));
        PhaseWindow secondRepetition = new PhaseWindow(PhaseWindow.INGESTION, at("11:00:01Z"), at("11:00:02Z"));

        Map<ResourceUsage.Key, ResourceUsage.Measures> usage = ResourceUsage.of(samples,
                Map.of("replicas-1-rep-1", List.of(INGESTION), "replicas-1-rep-2", List.of(secondRepetition)));

        assertEquals(List.of(10.0, 30.0), usage.get(new ResourceUsage.Key("replicas=1", PhaseWindow.INGESTION, "crawler")).cpuPerReplica()
                .stream().sorted().toList());
    }

    private static ContainerStats sample(String time, String container, double cpuPercent, long memoryMib) {
        return new ContainerStats(at(time), container, cpuPercent, memoryMib * MIB);
    }

    private static Instant at(String time) {
        return Instant.parse("2026-10-21T" + time);
    }
}
