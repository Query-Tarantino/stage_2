package org.ulpgc.tarantino.bench.charts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartsToolTest {

    @TempDir
    Path runs;

    @Test
    void drawsTheCoordinationChartWithAFooterUnderThePlot() throws IOException {
        Path run = Files.createDirectories(runs.resolve("20261110-153000-coordination-cost-3f2a9c1"));
        Files.write(run.resolve("coordination.csv"), List.of(
                "book_id,download_queue_wait_ms,download_ms,index_queue_wait_ms,indexing_ms,time_to_publication_ms",
                "84,100,1000,40,3000,4140",
                "1342,300,2000,60,5000,7360"));

        assertEquals(ChartsTool.DONE, ChartsTool.run(List.of(run.toString())));

        BufferedImage chart = ImageIO.read(run.resolve("charts").resolve("coordination.png").toFile());
        assertEquals(RunChart.WIDTH, chart.getWidth());
        assertEquals(RunChart.HEIGHT + RunChart.FOOTER_HEIGHT, chart.getHeight());
    }

    @Test
    void drawsTheCpuAndTheMemoryOfARunWithItsStats() throws IOException {
        Path run = Files.createDirectories(runs.resolve("20261110-153000-resource-usage-3f2a9c1"));
        Files.write(run.resolve("stats.csv"), List.of(
                "instant,container,cpu_percent,memory_bytes",
                "2026-10-21T10:00:00Z,stage_2-indexer-1,12.50,268435456",
                "2026-10-21T10:00:01Z,stage_2-indexer-1,112.47,536870912"));

        assertEquals(ChartsTool.DONE, ChartsTool.run(List.of(run.toString())));

        assertTrue(Files.exists(run.resolve("charts").resolve("cpu.png")));
        assertTrue(Files.exists(run.resolve("charts").resolve("memory.png")));
        assertFalse(Files.exists(run.resolve("charts").resolve("coordination.png")));
    }

    @Test
    void drawsTheIngestThroughputOfARunWithItsIngestCsv() throws IOException {
        Path run = Files.createDirectories(runs.resolve("20261031-134354-ingest-throughput-d403115"));
        Path trialLogs = Files.createDirectories(run.resolve("logs").resolve("replicas-1-workers-8-rep-1"));
        String transition = " INFO org.ulpgc.tarantino.common.operations.TransitionLog - transition entity=book id=";
        Files.write(trialLogs.resolve("crawler.log"), List.of(
                "crawler-1  | 2026-10-31T13:54:00Z" + transition + "1 from=QUEUED to=DOWNLOADING batch_id=- generation=- enqueued_at=- at=2026-10-31T13:54:00Z replica=a",
                "crawler-1  | 2026-10-31T13:54:01Z" + transition + "1 from=DOWNLOADING to=DOWNLOADED batch_id=- generation=- enqueued_at=- at=2026-10-31T13:54:01Z replica=a",
                "crawler-1  | 2026-10-31T13:54:02Z" + transition + "2 from=DOWNLOADING to=DOWNLOADED batch_id=- generation=- enqueued_at=- at=2026-10-31T13:54:02Z replica=a"));
        Files.writeString(run.resolve("ingest.csv"), "");

        assertEquals(ChartsTool.DONE, ChartsTool.run(List.of(run.toString())));

        assertTrue(Files.exists(run.resolve("charts").resolve("ingest-throughput.png")));
    }

    @Test
    void failsWhenTheRunHasNothingToDraw() throws IOException {
        Path run = Files.createDirectories(runs.resolve("20261110-153000-coordination-cost-3f2a9c1"));

        assertEquals(ChartsTool.FAILED, ChartsTool.run(List.of(run.toString())));
        assertFalse(Files.exists(run.resolve("charts")));
    }

    @Test
    void drawsTheRecoveryTimeOfTheRunsNextToItsOwnRunAndListsThem() throws IOException {
        Files.writeString(Files.createDirectories(runs.resolve("20261110-150000-recovery-04-r2-3f2a9c1")).resolve("checks.json"),
                "{\"recovery_seconds\": 12.5}");
        Path summary = Files.createDirectories(runs.resolve("20261111-090000-recovery-summary-3f2a9c1"));

        assertEquals(ChartsTool.DONE, ChartsTool.recovery(List.of(summary.toString())));

        assertTrue(Files.exists(summary.resolve("charts").resolve("recovery-seconds.png")));
        assertEquals(List.of("20261110-150000-recovery-04-r2-3f2a9c1"), Files.readAllLines(summary.resolve("sources.txt")));
    }

    @Test
    void failsWhenNoRecoveryRunHasItsChecks() throws IOException {
        Path summary = Files.createDirectories(runs.resolve("20261111-090000-recovery-summary-3f2a9c1"));

        assertEquals(ChartsTool.FAILED, ChartsTool.recovery(List.of(summary.toString())));
        assertFalse(Files.exists(summary.resolve("sources.txt")));
    }

    @Test
    void answersWrongUsageWithoutTheRunDirectory() {
        assertEquals(ChartsTool.WRONG_USAGE, ChartsTool.run(List.of()));
        assertEquals(ChartsTool.WRONG_USAGE, ChartsTool.recovery(List.of()));
    }
}
