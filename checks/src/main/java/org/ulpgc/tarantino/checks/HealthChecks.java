package org.ulpgc.tarantino.checks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.ulpgc.tarantino.checks.Assertions.assertTrue;

/**
 * {@code GET /health} of the four services, each on its own port (SPEC §6.5): straight from the host when the port
 * answers, otherwise through {@code docker compose exec}, because only nginx publishes ports (SPEC §2.4).
 */
final class HealthChecks {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthChecks.class);
    private static final Map<String, String> SERVICE_BY_PORT = Map.of(
            "7001", "crawler",
            "7002", "indexer",
            "7003", "query",
            "7004", "control");
    private static final String HEALTH = "/health";
    private static final String LOCALHOST = "http://localhost:";
    private static final String COMPOSE_FILE = "COMPOSE_FILE";
    private static final String COMPOSE_YAML = "docker-compose.yml";
    private static final String STATUS = "status";
    private static final String GET = "GET";
    private static final String HEADERS_FROM_BODY = "\r?\n\r?\n";
    private static final String LINE = "\r?\n";
    private static final String WORDS = "\\s+";
    private static final char HEADER_NAME_END = ':';
    private static final int STATUS_WORD = 1;
    private static final int OK = 200;
    private static final int SERVICE_UNAVAILABLE = 503;

    private final CheckedClient client;

    HealthChecks(CheckedClient client) {
        this.client = client;
    }

    void check() throws IOException, InterruptedException {
        LOGGER.info("Checking /health on service ports");
        for (Map.Entry<String, String> service : SERVICE_BY_PORT.entrySet()) {
            CheckedClient.Response response = health(service.getValue(), service.getKey());
            assertTrue(response.status() == OK || response.status() == SERVICE_UNAVAILABLE);
            assertTrue(response.json() != null && response.json().has(STATUS));
        }
    }

    private CheckedClient.Response health(String service, String port) throws IOException, InterruptedException {
        try {
            return client.get(LOCALHOST + port + HEALTH);
        } catch (IOException portNotPublished) {
            return throughCompose(service, port);
        }
    }

    private CheckedClient.Response throughCompose(String service, String port) throws IOException, InterruptedException {
        Process curl = new ProcessBuilder(composeExec(service, port)).redirectErrorStream(true).start();
        String output = new String(curl.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = curl.waitFor();
        if (exitCode != 0) {
            throw new IOException("docker compose exec failed for " + service + " with exit code " + exitCode + ": " + output);
        }
        return curlResponse(output);
    }

    private static List<String> composeExec(String service, String port) {
        List<String> command = new ArrayList<>(List.of("docker", "compose"));
        String composeFile = System.getenv(COMPOSE_FILE);
        if (composeFile == null || composeFile.isBlank()) {
            RepositoryFiles.find(COMPOSE_YAML).ifPresent(compose -> command.addAll(List.of("-f", compose.toString())));
        }
        command.addAll(List.of("exec", "-T", service, "curl", "-isS", LOCALHOST + port + HEALTH));
        return command;
    }

    private CheckedClient.Response curlResponse(String output) throws IOException {
        String[] headersAndBody = output.split(HEADERS_FROM_BODY, 2);
        String body = headersAndBody.length > 1 ? headersAndBody[1].trim() : "";
        List<String> lines = List.of(headersAndBody[0].split(LINE));
        return client.validateAndWrap(GET, HEALTH, status(lines.getFirst()), headers(lines.subList(1, lines.size())), body);
    }

    private static int status(String statusLine) {
        String[] words = statusLine.trim().split(WORDS);
        return words.length > STATUS_WORD ? Integer.parseInt(words[STATUS_WORD]) : OK;
    }

    private static Map<String, List<String>> headers(List<String> lines) {
        return lines.stream()
                .map(String::trim)
                .filter(line -> line.indexOf(HEADER_NAME_END) > 0)
                .collect(Collectors.groupingBy(line -> line.substring(0, line.indexOf(HEADER_NAME_END)).trim(), LinkedHashMap::new,
                        Collectors.mapping(line -> line.substring(line.indexOf(HEADER_NAME_END) + 1).trim(), Collectors.toList())));
    }
}
