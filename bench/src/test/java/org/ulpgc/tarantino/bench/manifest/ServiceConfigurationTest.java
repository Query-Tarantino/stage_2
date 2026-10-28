package org.ulpgc.tarantino.bench.manifest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceConfigurationTest {

    private static final List<String> INSPECT_ALL = List.of("docker", "inspect", "a1", "b2", "c3");

    @Test
    void countsTheReplicasOfEachServiceAndReadsItsJvmAndTarantinoVariables() {
        String inspected = """
                [
                  {"Config": {"Labels": {"com.docker.compose.service": "crawler"},
                              "Env": ["TARANTINO_LEASE_SECONDS=6", "TARANTINO_CRASH_AT=", "PATH=/opt/java/bin",
                                      "JAVA_VERSION=jdk-25.0.1+8", "JAVA_TOOL_OPTIONS=-Xmx512m -XX:+ExitOnOutOfMemoryError"]}},
                  {"Config": {"Labels": {"com.docker.compose.service": "crawler"},
                              "Env": ["TARANTINO_LEASE_SECONDS=6", "TARANTINO_CRASH_AT=",
                                      "JAVA_VERSION=jdk-25.0.1+8", "JAVA_TOOL_OPTIONS=-Xmx512m -XX:+ExitOnOutOfMemoryError"]}},
                  {"Config": {"Labels": {"com.docker.compose.service": "mongo"}, "Env": ["MONGO_VERSION=7.0.14"]}}
                ]
                """;
        CommandRunner runner = GitAndDockerStateTest.answering(Map.of(
                ServiceConfiguration.CONTAINER_IDS, "a1\nb2\nc3\n", INSPECT_ALL, inspected));

        Map<String, ServiceConfiguration> services = ServiceConfiguration.ofRunningServices(runner);

        assertEquals(List.of("crawler", "mongo"), List.copyOf(services.keySet()));
        assertEquals(new ServiceConfiguration(2, "jdk-25.0.1+8", "-Xmx512m -XX:+ExitOnOutOfMemoryError",
                Map.of("TARANTINO_CRASH_AT", "", "TARANTINO_LEASE_SECONDS", "6")), services.get("crawler"));
        assertEquals(new ServiceConfiguration(1, null, null, Map.of()), services.get("mongo"));
    }

    @Test
    void hasNoServicesWhenNothingRuns() {
        CommandRunner runner = GitAndDockerStateTest.answering(Map.of(ServiceConfiguration.CONTAINER_IDS, ""));

        assertEquals(Map.of(), ServiceConfiguration.ofRunningServices(runner));
    }
}
