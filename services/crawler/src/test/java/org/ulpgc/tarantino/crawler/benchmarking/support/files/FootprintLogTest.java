package org.ulpgc.tarantino.crawler.benchmarking.support.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.crawler.benchmarking.support.results.ResultRow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FootprintLogTest {

    @TempDir
    Path directory;

    @Test
    void keepsOneRowPerExactMeasureWhateverTheNumberOfProcesses() {
        Path exact = directory.resolve("exact.csv");
        for (int process = 0; process < 3; process++) {
            FootprintLog.append(exact, List.of(ResultRow.exact("folders", "term_count", 100, 106_410, "terms")));
        }

        assertEquals(List.of(ResultRow.exact("folders", "term_count", 100, 106_410, "terms")),
                FootprintLog.drain(exact, directory.resolve("samples.csv")));
        assertFalse(Files.exists(exact));
    }

    @Test
    void drainsSamplesAsTheirMeanAnd95ConfidenceHalfWidth() {
        Path samples = directory.resolve("samples.csv");
        List.of(10.0, 12.0, 14.0).forEach(value ->
                FootprintLog.append(samples, List.of(ResultRow.sample("json", "build_memory", 100, value, "bytes"))));

        ResultRow mean = FootprintLog.drain(directory.resolve("exact.csv"), samples).getFirst();

        // mean 12, standard deviation 2, t(0.975, 2) = 4.303: 4.303 × 2 / √3 = 4.968
        assertEquals(12.0, mean.value(), 1e-9);
        assertEquals(4.968, mean.error(), 1e-3);
    }

    @Test
    void cannotComputeTheErrorOfASingleSample() {
        Path samples = directory.resolve("samples.csv");
        FootprintLog.append(samples, List.of(ResultRow.sample("json", "index_memory", 100, 49e6, "bytes")));

        assertTrue(Double.isNaN(FootprintLog.drain(directory.resolve("exact.csv"), samples).getFirst().error()));
    }

    @Test
    void drainsNothingWhenNothingWasAppended() {
        assertEquals(List.of(), FootprintLog.drain(directory.resolve("exact.csv"), directory.resolve("samples.csv")));
    }
}
