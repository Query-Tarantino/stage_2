package org.ulpgc.tarantino.bench.manifest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.UncheckedIOException;
import java.util.List;

/** The Docker version and the resources it gives the containers (SPEC §9), from {@code docker info}. */
public record DockerState(String version, String operatingSystem, int cpus, long memoryBytes) {

    static final List<String> INFO = List.of("docker", "info", "--format", "{{json .}}");

    private static final ObjectMapper MAPPER = TarantinoJson.mapper();

    public static DockerState of(CommandRunner runner) {
        JsonNode info = tree(runner.output(INFO));
        return new DockerState(info.path("ServerVersion").asText(), info.path("OperatingSystem").asText(),
                info.path("NCPU").asInt(), info.path("MemTotal").asLong());
    }

    static JsonNode tree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
