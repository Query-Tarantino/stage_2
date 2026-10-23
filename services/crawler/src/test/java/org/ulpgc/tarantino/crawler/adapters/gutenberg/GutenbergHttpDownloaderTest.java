package org.ulpgc.tarantino.crawler.adapters.gutenberg;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.crawler.model.failure.DownloadException;
import org.ulpgc.tarantino.crawler.model.failure.FailureReason;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GutenbergHttpDownloaderTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final String TEXT = "Café *** START";

    private final Deque<Answer> answers = new ArrayDeque<>();
    private final List<String> requests = new ArrayList<>();
    private final List<Duration> waits = new ArrayList<>();
    private HttpServer mirror;
    private GutenbergHttpDownloader downloader;

    @BeforeEach
    void startMirror() throws IOException {
        mirror = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mirror.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI().getPath());
            reply(exchange, answers.isEmpty() ? new Answer(200, Map.of()) : answers.poll());
        });
        mirror.start();
        downloader = downloader("http://127.0.0.1:" + mirror.getAddress().getPort() + "/gutenberg-epub/%d/pg%d.txt");
    }

    @AfterEach
    void stopMirror() {
        mirror.stop(0);
    }

    @Test
    void returnsTheBodyDecodedAsUtf8() {
        assertEquals(TEXT, downloader.rawText(1342));
        assertEquals(List.of("/gutenberg-epub/1342/pg1342.txt"), requests);
    }

    @Test
    void mapsMissingBooksToNotFound() {
        answers.add(new Answer(404, Map.of()));

        assertEquals(FailureReason.NOT_FOUND, failure(1342));
    }

    @Test
    void mapsServerErrorsToNetworkErrorWithoutRetrying() {
        answers.add(new Answer(500, Map.of()));

        assertEquals(FailureReason.NETWORK_ERROR, failure(1342));
        assertEquals(1, requests.size());
    }

    @Test
    void mapsUnreachableServersToNetworkError() {
        assertEquals(FailureReason.NETWORK_ERROR,
                assertThrows(DownloadException.class, () -> downloader("http://localhost:1/%d/%d.txt").rawText(1)).reason());
    }

    @Test
    void retriesABusyMirrorAfterTheWaitItAsksFor() {
        answers.add(new Answer(429, Map.of("Retry-After", "120")));

        assertEquals(TEXT, downloader.rawText(1342));
        assertEquals(List.of(Duration.ofSeconds(120)), waits);
        assertEquals(2, requests.size());
    }

    @Test
    void readsTheWaitAsAnHttpDate() {
        answers.add(new Answer(503, Map.of("Retry-After", "Thu, 01 Oct 2026 12:01:30 GMT")));

        assertEquals(TEXT, downloader.rawText(1342));
        assertEquals(List.of(Duration.ofSeconds(90)), waits);
    }

    @Test
    void doublesTheWaitWhenTheMirrorDoesNotSayHowLong() {
        answers.addAll(List.of(new Answer(503, Map.of()), new Answer(503, Map.of()), new Answer(429, Map.of())));

        assertEquals(TEXT, downloader.rawText(1342));
        assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4)), waits);
    }

    @Test
    void makesEveryDownloadWaitWhileTheMirrorIsBusy() {
        answers.add(new Answer(429, Map.of("Retry-After", "60")));

        downloader.rawText(1342);
        // The clock stands still, so the second download starts while the mirror still asks to wait
        downloader.rawText(84);

        assertEquals(List.of(Duration.ofSeconds(60), Duration.ofSeconds(60)), waits);
    }

    @Test
    void givesUpWhenTheMirrorStaysBusy() {
        for (int answer = 0; answer < 6; answer++) {
            answers.add(new Answer(429, Map.of("Retry-After", "0")));
        }

        assertEquals(FailureReason.NETWORK_ERROR, failure(1342));
        assertEquals(6, requests.size());
    }

    @Test
    void givesUpWhenTheMirrorAsksToWaitTooLong() {
        answers.add(new Answer(503, Map.of("Retry-After", "3600")));

        assertEquals(FailureReason.NETWORK_ERROR, failure(1342));
        assertEquals(List.of(), waits);
        assertEquals(1, requests.size());
    }

    @Test
    void followsRedirectsWithinTheMirror() {
        answers.add(new Answer(302, Map.of("Location", "/moved/pg1342.txt")));

        assertEquals(TEXT, downloader.rawText(1342));
        assertEquals(List.of("/gutenberg-epub/1342/pg1342.txt", "/moved/pg1342.txt"), requests);
    }

    @Test
    void neverFollowsARedirectToAnotherHost() throws IOException {
        HttpServer otherHost = HttpServer.create(new InetSocketAddress(0), 0);
        List<String> otherRequests = new ArrayList<>();
        otherHost.createContext("/", exchange -> {
            otherRequests.add(exchange.getRequestURI().getPath());
            reply(exchange, new Answer(200, Map.of()));
        });
        otherHost.start();
        try {
            answers.add(new Answer(301, Map.of("Location", "http://localhost:" + otherHost.getAddress().getPort() + "/cache/epub/1342/pg1342.txt")));

            assertEquals(FailureReason.NETWORK_ERROR, failure(1342));
            assertEquals(List.of(), otherRequests);
        } finally {
            otherHost.stop(0);
        }
    }

    private FailureReason failure(int bookId) {
        return assertThrows(DownloadException.class, () -> downloader.rawText(bookId)).reason();
    }

    private GutenbergHttpDownloader downloader(String urlTemplate) {
        return new GutenbergHttpDownloader(urlTemplate, Clock.fixed(NOW, ZoneOffset.UTC), waits::add);
    }

    private static void reply(HttpExchange exchange, Answer answer) throws IOException {
        answer.headers().forEach(exchange.getResponseHeaders()::add);
        byte[] body = answer.status() == 200 ? TEXT.getBytes(StandardCharsets.UTF_8) : new byte[0];
        exchange.sendResponseHeaders(answer.status(), body.length == 0 ? -1 : body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private record Answer(int status, Map<String, String> headers) {
    }
}
