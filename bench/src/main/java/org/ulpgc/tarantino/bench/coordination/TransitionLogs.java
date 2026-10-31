package org.ulpgc.tarantino.bench.coordination;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The transition lines (SPEC §4.8) of a run's {@code logs/<service>.log} or of each {@code logs/<trial>/<service>.log}. */
public final class TransitionLogs {

    public static final String FOLDER = "logs";

    private static final String LOG_SUFFIX = ".log";

    private TransitionLogs() {
    }

    /** The transition lines of every service log in {@code folder}. */
    public static List<TransitionLine> of(Path folder) {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> file.getFileName().toString().endsWith(LOG_SUFFIX))
                    .sorted()
                    .flatMap(TransitionLogs::lines)
                    .flatMap(line -> TransitionLine.parse(line).stream())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The transition lines of each trial by trial name, in name order; empty in a run without trials. */
    public static Map<String, List<TransitionLine>> byTrial(Path logs) {
        try (Stream<Path> entries = Files.list(logs)) {
            return entries.filter(Files::isDirectory).sorted().collect(Collectors.toMap(trial -> trial.getFileName().toString(),
                    TransitionLogs::of, (first, second) -> first, LinkedHashMap::new));
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
