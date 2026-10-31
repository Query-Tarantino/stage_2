package org.ulpgc.tarantino.bench.manifest;

import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The {@code manifest.json} of a run (SPEC §9); {@code end} stays {@code null} until the run finishes, and
 * {@code trials} stays empty in a run without trials.
 */
public record Manifest(String runId, GitState git, List<String> command, Instant start, Instant end, HostInfo host,
                       DockerState docker, Map<String, ServiceConfiguration> services, int clientConcurrency,
                       List<Trial> trials) {

    public static final String FILE_NAME = "manifest.json";

    public Manifest withEnd(Instant end) {
        return new Manifest(runId, git, command, start, end, host, docker, services, clientConcurrency, trials);
    }

    public Manifest withTrial(Trial trial) {
        List<Trial> withNext = Stream.concat(trials.stream(), Stream.of(trial)).toList();
        return new Manifest(runId, git, command, start, end, host, docker, services, clientConcurrency, withNext);
    }

    public static Manifest readFrom(Path runDirectory) {
        try {
            return TarantinoJson.read(Files.readString(runDirectory.resolve(FILE_NAME)), Manifest.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path writeTo(Path runDirectory) {
        try {
            String json = TarantinoJson.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(this);
            return Files.writeString(runDirectory.resolve(FILE_NAME), json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
