package org.ulpgc.tarantino.bench.coordination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Derives a run's {@code coordination.csv} from the transition lines in its {@code logs/<service>.log} (SPEC §4.8 and §9). */
public final class CoordinationTool {

    static final int DONE = 0;
    static final int WRONG_USAGE = 2;
    static final String FILE_NAME = "coordination.csv";
    static final String HEADER = "book_id,download_queue_wait_ms,download_ms,index_queue_wait_ms,indexing_ms,time_to_publication_ms";

    private static final Logger LOGGER = LoggerFactory.getLogger(CoordinationTool.class);
    private static final String LOGS = "logs";
    private static final String LOG_SUFFIX = ".log";
    private static final String USAGE = "Usage: CoordinationTool <run directory>";

    private CoordinationTool() {
    }

    public static void main(String[] args) {
        System.exit(run(List.of(args)));
    }

    static int run(List<String> args) {
        if (args.size() != 1) {
            LOGGER.error(USAGE);
            return WRONG_USAGE;
        }
        Path runDirectory = Path.of(args.getFirst());
        List<BookCoordination> books = BookCoordination.of(transitions(runDirectory.resolve(LOGS)));
        Path written = write(runDirectory.resolve(FILE_NAME), books);
        LOGGER.info("Wrote the coordination times of {} published books to {}", books.size(), written);
        return DONE;
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

    private static Path write(Path csv, List<BookCoordination> books) {
        String rows = books.stream().map(CoordinationTool::row).map(row -> row + "\n").collect(Collectors.joining());
        try {
            return Files.writeString(csv, HEADER + "\n" + rows);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String row(BookCoordination book) {
        return Stream.of(book.downloadQueueWait(), book.download(), book.indexQueueWait(), book.indexing(), book.timeToPublication())
                .map(duration -> String.valueOf(duration.toMillis()))
                .collect(Collectors.joining(",", book.bookId() + ",", ""));
    }
}
