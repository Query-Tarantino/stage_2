package org.ulpgc.tarantino.bench.recovery;

import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/** Each {@code query} replica at one of the addresses the Compose DNS gives for its service name (SPEC §2.4). */
public class DnsQueryReplicas implements QueryReplicas {

    public static final String QUERY_HOST = "query";

    private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(5);
    private static final String FILTERS = "/search/filters";
    private static final String TOTAL_BOOKS = "total_books";
    private static final int NOT_ANSWERED = 0;

    private final String host;
    private final int port;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(CLIENT_TIMEOUT).build();

    public DnsQueryReplicas(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public List<Integer> snapshotBooks() {
        return addresses().stream().map(this::snapshotBooks).toList();
    }

    private List<InetAddress> addresses() {
        try {
            return Arrays.stream(InetAddress.getAllByName(host)).filter(Inet4Address.class::isInstance).toList();
        } catch (UnknownHostException e) {
            return List.of();
        }
    }

    private int snapshotBooks(InetAddress replica) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + replica.getHostAddress() + ":" + port + FILTERS))
                .timeout(CLIENT_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == Answer.OK ? TarantinoJson.mapper().readTree(response.body()).path(TOTAL_BOOKS).asInt() : NOT_ANSWERED;
        } catch (IOException e) {
            return NOT_ANSWERED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NOT_ANSWERED;
        }
    }
}
