package org.ulpgc.tarantino.control.adapters.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.control.ports.SnapshotRefreshes;

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

/**
 * {@code POST http://<ip>:7003/internal/snapshot/refresh} to every address the Compose DNS gives for {@code query}
 * (SPEC §11.4), which on the Compose network are IPv4. The JVM caches that list for 30 s; a new replica's own periodic
 * refresh covers the gap. A replica that does not answer is skipped.
 */
public class DnsSnapshotRefreshes implements SnapshotRefreshes {

    private static final Logger LOGGER = LoggerFactory.getLogger(DnsSnapshotRefreshes.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);
    private static final String PATH = "/internal/snapshot/refresh";

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final String host;
    private final int port;

    public DnsSnapshotRefreshes(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void refreshAll() {
        replicas().forEach(this::refresh);
    }

    private List<InetAddress> replicas() {
        try {
            return Arrays.stream(InetAddress.getAllByName(host)).filter(Inet4Address.class::isInstance).toList();
        } catch (UnknownHostException e) {
            LOGGER.warn("No {} replica to announce to: {}", host, e.getMessage());
            return List.of();
        }
    }

    private void refresh(InetAddress replica) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + replica.getHostAddress() + ":" + port + PATH))
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException e) {
            LOGGER.warn("Could not ask {} to refresh: {}", replica.getHostAddress(), e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
