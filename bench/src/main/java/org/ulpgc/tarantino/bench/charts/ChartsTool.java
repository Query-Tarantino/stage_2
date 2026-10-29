package org.ulpgc.tarantino.bench.charts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.bench.coordination.CoordinationTool;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** {@code bench charts <run directory>} draws the charts of a run from the files it has (SPEC §9). */
public final class ChartsTool {

    static final int DONE = 0;
    static final int FAILED = 1;
    static final int WRONG_USAGE = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(ChartsTool.class);
    private static final String HEADLESS = "java.awt.headless";

    private ChartsTool() {
    }

    public static int run(List<String> args) {
        if (args.size() != 1) {
            LOGGER.error("Usage: bench charts <run directory>");
            return WRONG_USAGE;
        }
        System.setProperty(HEADLESS, Boolean.TRUE.toString());
        Path runDirectory = Path.of(args.getFirst());
        List<Path> charts = coordination(runDirectory).toList();
        if (charts.isEmpty()) {
            LOGGER.error("{} has no {} to draw", runDirectory, CoordinationTool.FILE_NAME);
            return FAILED;
        }
        charts.forEach(chart -> LOGGER.info("Wrote {}", chart));
        return DONE;
    }

    private static Stream<Path> coordination(Path runDirectory) {
        Path csv = runDirectory.resolve(CoordinationTool.FILE_NAME);
        if (!Files.exists(csv)) {
            return Stream.empty();
        }
        return Stream.of(RunChart.write(CoordinationChart.of(CoordinationChart.phases(lines(csv))), runDirectory, CoordinationChart.NAME));
    }

    private static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
