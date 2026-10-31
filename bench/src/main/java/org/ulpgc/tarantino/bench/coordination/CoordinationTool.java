package org.ulpgc.tarantino.bench.coordination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Derives a run's {@code coordination.csv} from the transition lines in its {@code logs/<service>.log} or, in a run with
 * trials, in each {@code logs/<trial>/<service>.log}, with one row per book and trial and the {@code summary.csv} rows
 * of each configuration (SPEC §4.8 and §9).
 */
public final class CoordinationTool {

    static final int DONE = 0;
    static final int WRONG_USAGE = 2;
    public static final String FILE_NAME = "coordination.csv";
    public static final List<String> PHASE_COLUMNS = List.of("download_queue_wait_ms", "download_ms", "index_queue_wait_ms", "indexing_ms");
    static final List<String> TIME_COLUMNS = Stream.concat(PHASE_COLUMNS.stream(), Stream.of("time_to_publication_ms")).toList();
    static final String HEADER = "book_id," + String.join(",", TIME_COLUMNS);
    static final String TRIAL_HEADER = "trial," + HEADER;

    private static final Logger LOGGER = LoggerFactory.getLogger(CoordinationTool.class);
    private static final String LOGS = "logs";
    private static final String LOG_SUFFIX = ".log";
    private static final String SEPARATOR = ",";
    private static final String USAGE = "Usage: CoordinationTool <run directory>";

    private CoordinationTool() {
    }

    public static void main(String[] args) {
        System.exit(run(List.of(args)));
    }

    public static int run(List<String> args) {
        if (args.size() != 1) {
            LOGGER.error(USAGE);
            return WRONG_USAGE;
        }
        Path runDirectory = Path.of(args.getFirst());
        Path logs = runDirectory.resolve(LOGS);
        Map<String, List<BookCoordination>> booksByTrial = booksByTrial(logs);
        if (booksByTrial.isEmpty()) {
            List<String> rows = BookCoordination.of(transitions(logs)).stream().map(CoordinationTool::row).toList();
            LOGGER.info("Wrote the coordination times of {} published books to {}", rows.size(), write(runDirectory.resolve(FILE_NAME), HEADER, rows));
            return DONE;
        }
        List<String> rows = booksByTrial.entrySet().stream()
                .flatMap(trial -> trial.getValue().stream().map(book -> trial.getKey() + SEPARATOR + row(book)))
                .toList();
        LOGGER.info("Wrote the coordination times of {} published books to {}", rows.size(), write(runDirectory.resolve(FILE_NAME), TRIAL_HEADER, rows));
        CoordinationSummary.append(runDirectory, booksByTrial);
        return DONE;
    }

    private static Map<String, List<BookCoordination>> booksByTrial(Path logs) {
        try (Stream<Path> entries = Files.list(logs)) {
            return entries.filter(Files::isDirectory).sorted().collect(Collectors.toMap(trial -> trial.getFileName().toString(),
                    trial -> BookCoordination.of(transitions(trial)), (first, second) -> first, LinkedHashMap::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<TransitionLine> transitions(Path logs) {
        try (Stream<Path> files = Files.list(logs)) {
            return files.filter(file -> file.getFileName().toString().endsWith(LOG_SUFFIX))
                    .sorted()
                    .flatMap(CoordinationTool::lines)
                    .flatMap(line -> TransitionLine.parse(line).stream())
                    .toList();
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

    private static Path write(Path csv, String header, List<String> rows) {
        try {
            return Files.writeString(csv, Stream.concat(Stream.of(header), rows.stream()).map(line -> line + "\n").collect(Collectors.joining()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String row(BookCoordination book) {
        return book.times().stream()
                .map(duration -> String.valueOf(duration.toMillis()))
                .collect(Collectors.joining(SEPARATOR, book.bookId() + SEPARATOR, ""));
    }
}
