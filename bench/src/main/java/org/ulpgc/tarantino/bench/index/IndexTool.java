package org.ulpgc.tarantino.bench.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.bench.throughput.ThroughputReport;
import org.ulpgc.tarantino.bench.throughput.ThroughputWindow;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * {@code bench index-throughput <run directory>}: writes {@code index.csv}, one row per trial of an
 * {@code index-throughput} run with its factors, repetition and the window of {@link IndexThroughput}, and appends to
 * {@code summary.csv} the throughput of each configuration over its repetitions (SPEC §9).
 */
public final class IndexTool {

    static final int DONE = ThroughputReport.DONE;
    static final int FAILED = ThroughputReport.FAILED;
    static final int WRONG_USAGE = 2;
    public static final String FILE_NAME = "index.csv";

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexTool.class);
    private static final ThroughputReport<ThroughputWindow> REPORT = new ThroughputReport<>(FILE_NAME, IndexThroughput::of, List.of(
            new ThroughputReport.Series<>("index_throughput", "", Function.identity())));

    private IndexTool() {
    }

    public static int run(List<String> args) {
        if (args.size() != 1) {
            LOGGER.error("Usage: bench index-throughput <run directory>");
            return WRONG_USAGE;
        }
        return REPORT.writeFor(Path.of(args.getFirst()));
    }

    /** The throughput of each trial of a run with trials, in factor order; empty without {@code logs/}. */
    public static Map<String, ThroughputWindow> throughputs(Path runDirectory) {
        return REPORT.throughputs(runDirectory);
    }
}
