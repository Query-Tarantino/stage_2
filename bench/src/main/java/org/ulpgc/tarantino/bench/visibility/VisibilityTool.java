package org.ulpgc.tarantino.bench.visibility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.bench.coordination.TransitionLogs;
import org.ulpgc.tarantino.bench.run.TrialName;
import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code bench visibility <run dir>}: the delay from each batch's publication to each {@code query} replica's snapshot,
 * from the logs of every trial, into {@code visibility.csv} and {@code publication_visibility} in {@code summary.csv},
 * one configuration per trial without its repetition (SPEC §9).
 */
public final class VisibilityTool {

    public static final String FILE_NAME = "visibility.csv";
    public static final String METRIC = "publication_visibility";

    static final String HEADER = "trial,batch_id,replica,published_at,visible_at,visibility_ms";

    private static final Logger LOGGER = LoggerFactory.getLogger(VisibilityTool.class);
    private static final int DONE = 0;
    private static final int WRONG_USAGE = 2;
    private static final String UNIT = "ms";
    private static final String LOG_SUFFIX = ".log";

    private VisibilityTool() {
    }

    public static int run(List<String> args) {
        if (args.size() != 1) {
            LOGGER.error("Usage: bench visibility <run directory>");
            return WRONG_USAGE;
        }
        Path runDirectory = Path.of(args.getFirst());
        Map<String, List<BatchVisibility>> byTrial = byTrial(runDirectory.resolve(TransitionLogs.FOLDER));
        List<String> rows = byTrial.entrySet().stream()
                .flatMap(trial -> trial.getValue().stream().map(batch -> row(trial.getKey(), batch)))
                .toList();
        write(runDirectory.resolve(FILE_NAME), rows);
        appendSummary(runDirectory.resolve(SummaryStats.FILE_NAME), byTrial);
        LOGGER.info("Wrote the visibility of {} batches and replicas to {}", rows.size(), runDirectory.resolve(FILE_NAME));
        return DONE;
    }

    static Map<String, List<BatchVisibility>> byTrial(Path logs) {
        return TransitionLogs.byTrial(logs).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                trial -> BatchVisibility.of(trial.getValue(), snapshots(logs.resolve(trial.getKey()))),
                (first, second) -> first, LinkedHashMap::new));
    }

    private static List<SnapshotLine> snapshots(Path trialLogs) {
        try (Stream<Path> files = Files.list(trialLogs)) {
            return files.filter(file -> file.getFileName().toString().endsWith(LOG_SUFFIX))
                    .flatMap(VisibilityTool::lines)
                    .flatMap(line -> SnapshotLine.parse(line).stream())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void appendSummary(Path summaryCsv, Map<String, List<BatchVisibility>> byTrial) {
        Map<String, List<Long>> byConfiguration = byTrial.entrySet().stream().collect(Collectors.groupingBy(
                trial -> TrialName.configuration(trial.getKey()), LinkedHashMap::new,
                Collectors.flatMapping(trial -> trial.getValue().stream().map(batch -> batch.visibility().toMillis()),
                        Collectors.toList())));
        byConfiguration.forEach((configuration, millis) -> {
            try {
                SummaryStats.appendToSummary(summaryCsv, METRIC, configuration, SummaryStats.calculate(millis), UNIT);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static String row(String trial, BatchVisibility batch) {
        return String.join(",", trial, batch.batchId(), batch.replica(), batch.publishedAt().toString(),
                batch.visibleAt().toString(), String.valueOf(batch.visibility().toMillis()));
    }

    private static void write(Path csv, List<String> rows) {
        try {
            Files.writeString(csv, Stream.concat(Stream.of(HEADER), rows.stream()).map(line -> line + "\n").collect(Collectors.joining()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Stream<String> lines(Path log) {
        try {
            return Files.readAllLines(log).stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
