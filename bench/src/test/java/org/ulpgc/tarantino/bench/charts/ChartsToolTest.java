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
    void failsWhenTheRunHasNothingToDraw() throws IOException {
        Path run = Files.createDirectories(runs.resolve("20261110-153000-coordination-cost-3f2a9c1"));

        assertEquals(ChartsTool.FAILED, ChartsTool.run(List.of(run.toString())));
        assertFalse(Files.exists(run.resolve("charts")));
    }

    @Test
    void answersWrongUsageWithoutTheRunDirectory() {
        assertEquals(ChartsTool.WRONG_USAGE, ChartsTool.run(List.of()));
    }
}
