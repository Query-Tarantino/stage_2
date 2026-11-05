package org.ulpgc.tarantino.bench.containers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Appends a row to {@code host.csv} on every tick while {@code bench stats} records {@code docker stats} (SPEC §9).
 * Each row is flushed at once, so stopping the recording at any moment keeps every sample taken so far.
 */
public final class HostRecorder implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(HostRecorder.class);
    private static final Duration STOP_WAIT = Duration.ofSeconds(1);

    private final ScheduledExecutorService ticks = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("host-stats").factory());
    private final BufferedWriter rows;
    private final Supplier<Optional<HostStats>> samples;

    private HostRecorder(BufferedWriter rows, Supplier<Optional<HostStats>> samples) {
        this.rows = rows;
        this.samples = samples;
    }

    /**
     * Starts sampling into {@code csv} every {@code period}, writing the header first if the file is new. A CPU load is
     * the share of time busy since the previous reading, so the first reading is discarded and the first row comes one
     * period later.
     */
    public static HostRecorder start(Path csv, Duration period, Supplier<Optional<HostStats>> samples) {
        boolean newFile = Files.notExists(csv);
        try {
            BufferedWriter rows = Files.newBufferedWriter(csv, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            HostRecorder recorder = new HostRecorder(rows, samples);
            if (newFile) {
                recorder.writeLine(HostStats.CSV_HEADER);
            }
            samples.get();
            recorder.ticks.scheduleAtFixedRate(recorder::record, period.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
            return recorder;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void record() {
        try {
            Optional<HostStats> sample = samples.get();
            if (sample.isPresent()) {
                writeLine(sample.get().csvRow());
            }
        } catch (IOException e) {
            LOGGER.warn("Could not write a host sample: {}", e.getMessage());
        }
    }

    private void writeLine(String line) throws IOException {
        rows.write(line);
        rows.newLine();
        rows.flush();
    }

    @Override
    public void close() {
        ticks.shutdownNow();
        try {
            ticks.awaitTermination(STOP_WAIT.toMillis(), TimeUnit.MILLISECONDS);
            rows.close();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
