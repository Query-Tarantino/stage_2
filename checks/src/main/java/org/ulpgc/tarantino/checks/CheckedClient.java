package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class CheckedClient {

    private static final ObjectMapper MAPPER = TarantinoJson.mapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client;
    private final OpenApiValidator validator;

    public CheckedClient(OpenApiValidator validator) {
        this.client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        this.validator = validator;
    }

    public OpenApiValidator validator() {
        return validator;
    }

    public Response get(String url) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return validateAndWrap("GET", pathAndQuery(uri), response);
    }

    public Response post(String url, String body) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        HttpRequest.BodyPublisher publisher = body == null || body.isBlank()
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(TIMEOUT).POST(publisher);
        if (body != null && !body.isBlank()) {
            builder.header("Content-Type", "application/json");
        }
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return validateAndWrap("POST", pathAndQuery(uri), response);
    }

    public Response validateAndWrap(String method, String path, int status, Map<String, List<String>> headers, String body) throws IOException {
        validator.validateResponse(method, path, status, headers, body);
        JsonNode json = (body != null && !body.isBlank() && isJson(headers)) ? MAPPER.readTree(body) : null;
        return new Response(status, headers, body, json);
    }

    private Response validateAndWrap(String method, String path, HttpResponse<String> response) throws IOException {
        return validateAndWrap(method, path, response.statusCode(), response.headers().map(), response.body());
    }

    private static boolean isJson(Map<String, List<String>> headers) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase("Content-Type"))
                .flatMap(entry -> entry.getValue().stream())
                .anyMatch(mediaType -> mediaType.contains("json"));
    }

    private static String pathAndQuery(URI uri) {
        String path = uri.getRawPath();
        String query = uri.getRawQuery();
        return query == null ? path : path + "?" + query;
    }

    public record Response(int status, Map<String, List<String>> headers, String body, JsonNode json) {
        public String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream())
                    .findFirst()
                    .orElse(null);
        }
    }
}
