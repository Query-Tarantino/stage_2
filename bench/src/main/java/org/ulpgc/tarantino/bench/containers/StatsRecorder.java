package org.ulpgc.tarantino.bench.containers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.Optional;

/**
 * Appends to {@code stats.csv} a row per container for each {@code docker stats} sample it reads, stamped when read
 * (SPEC §9). Each row is flushed at once, so stopping the recording at any moment keeps every sample read so far.
 */
public class StatsRecorder {

    private final Clock clock;

    public StatsRecorder(Clock clock) {
        this.clock = clock;
    }

    /** Records until {@code samples} ends and answers how many rows it wrote. */
    public long record(BufferedReader samples, Path csv) {
        boolean newFile = Files.notExists(csv);
        try (BufferedWriter rows = Files.newBufferedWriter(csv, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (newFile) {
                writeLine(rows, ContainerStats.CSV_HEADER);
            }
            long written = 0;
            for (String line = samples.readLine(); line != null; line = samples.readLine()) {
                Optional<ContainerStats> stats = ContainerStats.parse(line, clock.instant());
                if (stats.isPresent()) {
                    writeLine(rows, stats.get().csvRow());
                    written++;
                }
            }
            return written;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeLine(BufferedWriter rows, String line) throws IOException {
        rows.write(line);
        rows.newLine();
        rows.flush();
    }
}
