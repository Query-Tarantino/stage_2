package org.ulpgc.tarantino.bench.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.bench.coordination.TransitionLogs;
import org.ulpgc.tarantino.bench.stats.SummaryStats;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code bench recovery-delay <output run dir> <recovery run dir>…}: for each book of each recovery run, the delay from
 * {@code failed_at} to its requeue and to its next claim, into {@code recovery-delay.csv}, with {@code requeue_delay} and
 * {@code reclaim_delay} in {@code summary.csv} per mode and replicas (SPEC §9).
 */
public final class RecoveryDelayTool {

    public static final String FILE_NAME = "recovery-delay.csv";

    static final String HEADER = "source_run_id,mode,replicas,book_id,status,requeue_delay_ms,reclaim_delay_ms";

    private static final Logger LOGGER = LoggerFactory.getLogger(RecoveryDelayTool.class);
    private static final int DONE = 0;
    private static final int WRONG_USAGE = 2;
    private static final String UNIT = "ms";
    private static final String FAILED_AT = "failed_at";

    private RecoveryDelayTool() {
    }

    public static int run(List<String> args) {
        if (args.size() < 2) {
            LOGGER.error("Usage: bench recovery-delay <output run dir> <recovery run dir>...");
            return WRONG_USAGE;
        }
        Path output = Path.of(args.getFirst());
        List<SourceRun> sources = args.subList(1, args.size()).stream().map(Path::of).map(RecoveryDelayTool::source).toList();
        write(output.resolve(FILE_NAME), sources.stream().flatMap(SourceRun::rows).toList());
        appendSummary(output.resolve(SummaryStats.FILE_NAME), sources);
        LOGGER.info("Wrote the recovery delays of {} runs to {}", sources.size(), output.resolve(FILE_NAME));
        return DONE;
    }

    private static SourceRun source(Path runDirectory) {
        String runId = runDirectory.getFileName().toString();
        RecoveryRunName name = RecoveryRunName.parse(runId)
                .orElseThrow(() -> new IllegalArgumentException("Not a recovery run: " + runDirectory));
        List<BookRecoveryDelay> books = BookRecoveryDelay.of(TransitionLogs.of(runDirectory.resolve(TransitionLogs.FOLDER)),
                failedAt(runDirectory));
        return new SourceRun(runId, name, books);
    }

    private static Instant failedAt(Path runDirectory) {
        try {
            JsonNode checks = TarantinoJson.mapper().readTree(Files.readString(runDirectory.resolve(RecoveryChecks.FILE_NAME)));
            return Instant.parse(checks.get(FAILED_AT).asText());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void appendSummary(Path summaryCsv, List<SourceRun> sources) {
        Map<String, List<BookRecoveryDelay>> byConfiguration = sources.stream().collect(Collectors.groupingBy(
                source -> "mode=" + source.name().mode() + ",replicas=" + source.name().replicas(), LinkedHashMap::new,
                Collectors.flatMapping(source -> source.books().stream(), Collectors.toList())));
        byConfiguration.forEach((configuration, books) -> {
            append(summaryCsv, "requeue_delay", configuration,
                    books.stream().flatMap(book -> book.requeueDelay().stream()).map(Duration::toMillis).toList());
            append(summaryCsv, "reclaim_delay", configuration, books.stream().map(book -> book.reclaimDelay().toMillis()).toList());
        });
    }

    private static void append(Path summaryCsv, String metric, String configuration, List<Long> millis) {
        try {
            SummaryStats.appendToSummary(summaryCsv, metric, configuration, SummaryStats.calculate(millis), UNIT);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path csv, List<String> rows) {
        try {
            Files.writeString(csv, Stream.concat(Stream.of(HEADER), rows.stream()).map(line -> line + "\n").collect(Collectors.joining()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record SourceRun(String runId, RecoveryRunName name, List<BookRecoveryDelay> books) {

        Stream<String> rows() {
            return books.stream().map(book -> String.join(",", runId, name.mode(), String.valueOf(name.replicas()),
                    String.valueOf(book.bookId()), book.status(),
                    book.requeueDelay().map(delay -> String.valueOf(delay.toMillis())).orElse(""),
                    String.valueOf(book.reclaimDelay().toMillis())));
        }
    }
}
