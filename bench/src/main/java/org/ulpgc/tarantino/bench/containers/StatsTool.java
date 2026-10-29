package org.ulpgc.tarantino.bench.containers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/**
 * Records {@code stats.csv} on the host, where {@code docker} is, from {@code docker stats} streaming one sample per
 * second (SPEC §9). The script launches it in the background and kills it when the measured phase ends.
 */
public final class StatsTool {

    static final int DONE = 0;
    static final int WRONG_USAGE = 2;
    public static final String FILE_NAME = "stats.csv";

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsTool.class);
    private static final List<String> DOCKER_STATS = List.of("docker", "stats", "--format", "{{json .}}");
    private static final String USAGE = "Usage: StatsTool <run directory>";

    private StatsTool() {
    }

    public static void main(String[] args) {
        System.exit(run(List.of(args)));
    }

    public static int run(List<String> args) {
        if (args.size() != 1) {
            LOGGER.error(USAGE);
            return WRONG_USAGE;
        }
        Path csv = Path.of(args.getFirst()).resolve(FILE_NAME);
        Process dockerStats = start();
        Runtime.getRuntime().addShutdownHook(new Thread(dockerStats::destroy));
        try (BufferedReader samples = new BufferedReader(new InputStreamReader(dockerStats.getInputStream(), StandardCharsets.UTF_8))) {
            LOGGER.info("Recorded {} container samples in {}", new StatsRecorder(Clock.systemUTC()).record(samples, csv), csv);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return DONE;
    }

    private static Process start() {
        try {
            return new ProcessBuilder(DOCKER_STATS).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
