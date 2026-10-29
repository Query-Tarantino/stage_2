package org.ulpgc.tarantino.bench.manifest;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * How a service ran (SPEC §9): its replicas, its JVM and flags, and every {@code TARANTINO_*} variable, which include
 * its threads. They come from the containers themselves, so they are the values the run used.
 */
public record ServiceConfiguration(int replicas, String javaVersion, String javaToolOptions, Map<String, String> tarantinoVariables) {

    static final List<String> CONTAINER_IDS = List.of("docker", "compose", "ps", "--all", "--quiet");
    static final List<String> INSPECT = List.of("docker", "inspect");

    private static final String SERVICE_LABEL = "com.docker.compose.service";
    private static final String JAVA_VERSION = "JAVA_VERSION";
    private static final String JAVA_TOOL_OPTIONS = "JAVA_TOOL_OPTIONS";
    private static final String TARANTINO_PREFIX = "TARANTINO_";
    private static final String ASSIGNMENT = "=";

    /** Each service of the Compose project by name, stopped containers included, so a run opened after a crash still records the replica that went down. */
    public static Map<String, ServiceConfiguration> ofComposeServices(CommandRunner runner) {
        List<String> containerIds = runner.output(CONTAINER_IDS).lines().filter(line -> !line.isBlank()).toList();
        if (containerIds.isEmpty()) {
            return Map.of();
        }
        JsonNode containers = DockerState.tree(runner.output(Stream.concat(INSPECT.stream(), containerIds.stream()).toList()));
        return StreamSupport.stream(containers.spliterator(), false)
                .collect(Collectors.groupingBy(container -> container.path("Config").path("Labels").path(SERVICE_LABEL).asText(),
                        TreeMap::new, Collectors.collectingAndThen(Collectors.toList(), ServiceConfiguration::of)));
    }

    private static ServiceConfiguration of(List<JsonNode> replicas) {
        Map<String, String> environment = environment(replicas.getFirst());
        Map<String, String> tarantinoVariables = environment.entrySet().stream()
                .filter(variable -> variable.getKey().startsWith(TARANTINO_PREFIX))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first, TreeMap::new));
        return new ServiceConfiguration(replicas.size(), environment.get(JAVA_VERSION), environment.get(JAVA_TOOL_OPTIONS), tarantinoVariables);
    }

    private static Map<String, String> environment(JsonNode container) {
        return StreamSupport.stream(container.path("Config").path("Env").spliterator(), false)
                .map(JsonNode::asText)
                .filter(variable -> variable.contains(ASSIGNMENT))
                .collect(Collectors.toMap(variable -> variable.substring(0, variable.indexOf(ASSIGNMENT)),
                        variable -> variable.substring(variable.indexOf(ASSIGNMENT) + 1), (first, second) -> second));
    }
}
