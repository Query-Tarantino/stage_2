package org.ulpgc.tarantino.bench.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.bench.throughput.ThroughputReport;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * {@code bench ingest-throughput <run directory>}: writes {@code ingest.csv}, one row per trial of an
 * {@code ingest-throughput} run with its factors, repetition and both windows of {@link IngestThroughput}, and appends
 * to {@code summary.csv} the throughput of each configuration over its repetitions (SPEC §9).
 */
public final class IngestTool {

    static final int DONE = ThroughputReport.DONE;
    static final int FAILED = ThroughputReport.FAILED;
    static final int WRONG_USAGE = 2;
    public static final String FILE_NAME = "ingest.csv";

    private static final Logger LOGGER = LoggerFactory.getLogger(IngestTool.class);
    private static final ThroughputReport<IngestThroughput> REPORT = new ThroughputReport<>(FILE_NAME, IngestThroughput::of, List.of(
            new ThroughputReport.Series<>("ingest_throughput", "", IngestThroughput::whole),
            new ThroughputReport.Series<>("ingest_throughput_steady", "steady_", IngestThroughput::steady)));

    private IngestTool() {
    }

    public static int run(List<String> args) {
        if (args.size() != 1) {
            LOGGER.error("Usage: bench ingest-throughput <run directory>");
            return WRONG_USAGE;
        }
        return REPORT.writeFor(Path.of(args.getFirst()));
    }

    /** The throughput of each trial of a run with trials, in factor order; empty without {@code logs/}. */
    public static Map<String, IngestThroughput> throughputs(Path runDirectory) {
        return REPORT.throughputs(runDirectory);
    }
}
