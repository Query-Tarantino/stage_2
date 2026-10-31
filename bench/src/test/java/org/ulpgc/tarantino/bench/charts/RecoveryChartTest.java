package org.ulpgc.tarantino.bench.charts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.knowm.xchart.CategoryChart;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecoveryChartTest {

    @TempDir
    Path runs;

    @Test
    void keepsTheLatestRunOfEachScriptAndReplicaCount() throws IOException {
        recoveryRun("20261110-150000-recovery-04-r2-3f2a9c1", 30.0);
        recoveryRun("20261110-160000-recovery-04-r2-4b3c2d1", 12.5);
        recoveryRun("20261110-150500-recovery-04-r1-3f2a9c1", 9.0);
        recoveryRun("20261110-151000-recovery-01-r3-3f2a9c1", 7.25);
        Files.createDirectories(runs.resolve("20261110-152000-recovery-02-r1-3f2a9c1"));
        Files.writeString(Files.createDirectories(runs.resolve("20261110-153000-search-latency-3f2a9c1")).resolve("checks.json"), "{}");

        assertEquals(List.of(
                new RecoveryChart.RecoveryRun("20261110-151000-recovery-01-r3-3f2a9c1", "01", 3, 7.25),
                new RecoveryChart.RecoveryRun("20261110-150500-recovery-04-r1-3f2a9c1", "04", 1, 9.0),
                new RecoveryChart.RecoveryRun("20261110-160000-recovery-04-r2-4b3c2d1", "04", 2, 12.5)),
                RecoveryChart.latestRuns(runs));
    }

    @Test
    void drawsOneSeriesPerReplicaCountWithNoBarWhereAScriptDidNotRunWithThem() {
        CategoryChart chart = RecoveryChart.of(List.of(
                new RecoveryChart.RecoveryRun("20261110-150500-recovery-13-r2-3f2a9c1", "13", 2, 9.0),
                new RecoveryChart.RecoveryRun("20261110-151000-recovery-14-r2-3f2a9c1", "14", 2, 21.0),
                new RecoveryChart.RecoveryRun("20261110-151500-recovery-13-r1-3f2a9c1", "13", 1, 8.0)));

        assertEquals(List.of("N = 1", "N = 2"), List.copyOf(chart.getSeriesMap().keySet()));
        assertEquals(List.of("13", "14"), chart.getSeriesMap().get("N = 1").getXData());
        assertEquals(Arrays.asList(8.0, null), chart.getSeriesMap().get("N = 1").getYData());
        assertEquals(List.of(9.0, 21.0), chart.getSeriesMap().get("N = 2").getYData());
    }

    @Test
    void startsTheAxisAtZeroAndLeavesRoomAboveTheTallestBarSoEqualBarsStillGetTicks() {
        CategoryChart chart = RecoveryChart.of(List.of(
                new RecoveryChart.RecoveryRun("20261110-150500-recovery-13-r1-3f2a9c1", "13", 1, 20.0),
                new RecoveryChart.RecoveryRun("20261110-151000-recovery-13-r2-3f2a9c1", "13", 2, 20.0)));

        assertEquals(0.0, chart.getStyler().getYAxisMin());
        assertEquals(22.0, chart.getStyler().getYAxisMax(), 1e-9);
    }

    private void recoveryRun(String runId, double recoverySeconds) throws IOException {
        Files.writeString(Files.createDirectories(runs.resolve(runId)).resolve("checks.json"),
                "{\"control_status\": {}, \"search\": {}, \"recovery_seconds\": " + recoverySeconds + "}");
    }
}
