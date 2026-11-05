package org.ulpgc.tarantino.checks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.config.TarantinoService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;
import static org.ulpgc.tarantino.checks.ApiNames.HEALTH;
import static org.ulpgc.tarantino.checks.ApiNames.STATUS;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertTrue;

/**
 * {@code GET /health} of each service on its own port (SPEC §6.5): from the host when the port is published, and
 * otherwise inside its container with {@code docker compose exec}, because only nginx publishes ports (SPEC §6.1).
 */
final class HealthChecks {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthChecks.class);
    private static final String COMPOSE_FILE_VARIABLE = "COMPOSE_FILE";
    private static final String COMPOSE_FILE = "docker-compose.yml";
    private static final String GET = "GET";
    private static final String BLANK_LINE = "\r?\n\r?\n";
    private static final String LINE_BREAK = "\r?\n";
    private static final String SPACES = "\\s+";
    private static final int STATUS_TOKEN = 1;
    private static final char HEADER_SEPARATOR = ':';

    private final CheckedClient client;

    HealthChecks(CheckedClient client) {
        this.client = client;
    }

    void check() throws IOException, InterruptedException {
        LOGGER.info("Checking /health on service ports");
        for (TarantinoService service : TarantinoService.values()) {
            String name = name(service);
            CheckedClient.Response response = health(service);
            assertTrue(response.status() == HTTP_OK || response.status() == HTTP_UNAVAILABLE, "/health of " + name + " answers 200 or 503");
            assertTrue(response.json() != null && response.json().has(STATUS), "/health of " + name + " has a status");
        }
    }

    private CheckedClient.Response health(TarantinoService service) throws IOException, InterruptedException {
        try {
            return client.get(localUrl(service));
        } catch (IOException exception) {
            return healthInsideContainer(service);
        }
    }

    private CheckedClient.Response healthInsideContainer(TarantinoService service) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(composeExec(service)).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("docker compose exec failed for " + name(service) + " with exit code " + exitCode + ": " + output);
        }
        return parseCurlResponse(output);
    }

    private static List<String> composeExec(TarantinoService service) {
        List<String> command = new ArrayList<>(List.of("docker", "compose"));
        String composeFiles = System.getenv(COMPOSE_FILE_VARIABLE);
        if (composeFiles == null || composeFiles.isBlank()) {
            RepositoryFiles.find(COMPOSE_FILE).ifPresent(path -> command.addAll(List.of("-f", path.toString())));
        }
        command.addAll(List.of("exec", "-T", name(service), "curl", "-isS", localUrl(service)));
        return command;
    }

    private CheckedClient.Response parseCurlResponse(String output) throws IOException {
        String[] headersAndBody = output.split(BLANK_LINE, 2);
        List<String> lines = List.of(headersAndBody[0].split(LINE_BREAK));
        String body = headersAndBody.length > 1 ? headersAndBody[1].trim() : "";
        return client.validateAndWrap(GET, HEALTH, status(lines.getFirst()), headers(lines.subList(1, lines.size())), body);
    }

    private static int status(String statusLine) {
        String[] tokens = statusLine.trim().split(SPACES);
        return tokens.length > STATUS_TOKEN ? Integer.parseInt(tokens[STATUS_TOKEN]) : HTTP_OK;
    }

    private static Map<String, List<String>> headers(List<String> lines) {
        return lines.stream()
                .map(String::trim)
                .filter(line -> line.indexOf(HEADER_SEPARATOR) > 0)
                .collect(Collectors.groupingBy(line -> line.substring(0, line.indexOf(HEADER_SEPARATOR)).trim(), LinkedHashMap::new,
                        Collectors.mapping(line -> line.substring(line.indexOf(HEADER_SEPARATOR) + 1).trim(), Collectors.toList())));
    }

    private static String localUrl(TarantinoService service) {
        return "http://localhost:" + service.defaultPort() + HEALTH;
    }

    private static String name(TarantinoService service) {
        return service.name().toLowerCase(Locale.ROOT);
    }
}
