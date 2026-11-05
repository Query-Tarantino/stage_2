package org.ulpgc.tarantino.checks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.net.HttpURLConnection.HTTP_OK;

/**
 * Sends one request with {@code curl} from inside a container of the stack, through {@code docker compose exec}, for
 * the ports only the Compose network reaches: each service's own port and nginx's internal port (SPEC §6.1). The
 * answer is validated against {@code api/openapi.yaml} like any other.
 */
final class InsideCompose {

    private static final String COMPOSE_FILE_VARIABLE = "COMPOSE_FILE";
    private static final String COMPOSE_FILE = "docker-compose.yml";
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String JSON_CONTENT = "Content-Type: application/json";
    private static final String BLANK_LINE = "\r?\n\r?\n";
    private static final String LINE_BREAK = "\r?\n";
    private static final String SPACES = "\\s+";
    private static final int STATUS_TOKEN = 1;
    private static final char HEADER_SEPARATOR = ':';

    private final CheckedClient client;

    InsideCompose(CheckedClient client) {
        this.client = client;
    }

    /** {@code GET url} from inside {@code service}, validated as {@code apiPath}. */
    CheckedClient.Response get(String service, String url, String apiPath) throws IOException, InterruptedException {
        return send(service, List.of("curl", "-isS", url), GET, apiPath);
    }

    /** {@code POST url} without a body from inside {@code service}, validated as {@code apiPath}. */
    CheckedClient.Response post(String service, String url, String apiPath) throws IOException, InterruptedException {
        return send(service, List.of("curl", "-isS", "-X", POST, url), POST, apiPath);
    }

    /** {@code POST url} with a JSON body from inside {@code service}, validated as {@code apiPath}. */
    CheckedClient.Response postJson(String service, String url, String json, String apiPath) throws IOException, InterruptedException {
        return send(service, List.of("curl", "-isS", "-X", POST, "-H", JSON_CONTENT, "--data", json, url), POST, apiPath);
    }

    private CheckedClient.Response send(String service, List<String> curl, String method, String apiPath)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(composeExec(service, curl)).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("docker compose exec failed in " + service + " with exit code " + exitCode + ": " + output);
        }
        return parse(output, method, apiPath);
    }

    private static List<String> composeExec(String service, List<String> curl) {
        List<String> command = new ArrayList<>(List.of("docker", "compose"));
        String composeFiles = System.getenv(COMPOSE_FILE_VARIABLE);
        if (composeFiles == null || composeFiles.isBlank()) {
            RepositoryFiles.find(COMPOSE_FILE).ifPresent(path -> command.addAll(List.of("-f", path.toString())));
        }
        command.addAll(List.of("exec", "-T", service));
        command.addAll(curl);
        return command;
    }

    private CheckedClient.Response parse(String output, String method, String apiPath) throws IOException {
        String[] headersAndBody = output.split(BLANK_LINE, 2);
        List<String> lines = List.of(headersAndBody[0].split(LINE_BREAK));
        String body = headersAndBody.length > 1 ? headersAndBody[1].trim() : "";
        return client.validateAndWrap(method, apiPath, status(lines.getFirst()), headers(lines.subList(1, lines.size())), body);
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
}
