package org.ulpgc.tarantino.bench.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Asks nginx: the front-end port for books and searches, the operations port for {@code control} (SPEC §6.1). */
public class HttpStack implements Stack {

    public static final String FRONT_END = "http://nginx:8080";
    public static final String OPERATIONS = "http://nginx:8081";

    private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(5);

    private final String frontEnd;
    private final String operations;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(CLIENT_TIMEOUT).build();

    public HttpStack(String frontEnd, String operations) {
        this.frontEnd = frontEnd;
        this.operations = operations;
    }

    @Override
    public Answer controlStatus() {
        return get(operations + "/control/status");
    }

    @Override
    public Answer book(int bookId) {
        return get(frontEnd + "/books/" + bookId);
    }

    @Override
    public Answer search(String query, int offset, int limit) {
        return get(frontEnd + "/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20")
                + "&offset=" + offset + "&limit=" + limit);
    }

    private Answer get(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(CLIENT_TIMEOUT).GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new Answer(response.statusCode(), body(response.body()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode body(String json) {
        try {
            return TarantinoJson.mapper().readTree(json);
        } catch (IOException e) {
            return TarantinoJson.mapper().missingNode();
        }
    }
}
