package org.ulpgc.tarantino.crawler.adapters.gutenberg;

import org.ulpgc.tarantino.crawler.model.failure.DownloadException;
import org.ulpgc.tarantino.crawler.model.failure.FailureReason;
import org.ulpgc.tarantino.crawler.ports.BookDownloader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Downloads books from the official Project Gutenberg mirror of Old Dominion University (SPEC §4), never from
 * www.gutenberg.org, whose robot policy forbids automated access: redirects are followed only within the mirror.
 * The control service downloads several books at once through one downloader, so when the mirror answers that it is
 * busy (429 or 503) every download waits as long as it asks before its next request.
 */
public class GutenbergHttpDownloader implements BookDownloader {

    private static final String MIRROR_URL_TEMPLATE = "https://mirror.cs.odu.edu/gutenberg-epub/%d/pg%d.txt";
    private static final String USER_AGENT = "query-tarantino/1.0 (ULPGC Big Data course project)";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);
    private static final Set<Integer> BUSY = Set.of(429, 503);
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_RETRIES = 5;
    private static final Duration FIRST_WAIT = Duration.ofSeconds(1);
    private static final Duration LONGEST_WAIT = Duration.ofMinutes(5);
    private static final Pattern SECONDS = Pattern.compile("\\d{1,9}");

    private final String urlTemplate;
    private final Clock clock;
    private final Pause pause;
    private final AtomicReference<Instant> pausedUntil = new AtomicReference<>(Instant.EPOCH);
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(TIMEOUT)
            .build();

    public GutenbergHttpDownloader() {
        this(MIRROR_URL_TEMPLATE, Clock.systemUTC(), Thread::sleep);
    }

    GutenbergHttpDownloader(String urlTemplate, Clock clock, Pause pause) {
        this.urlTemplate = urlTemplate;
        this.clock = clock;
        this.pause = pause;
    }

    /** Retries while the mirror is busy, waiting what its Retry-After asks or 1, 2, 4, 8 and 16 seconds. */
    @Override
    public String rawText(int bookId) throws DownloadException {
        URI uri = URI.create(urlTemplate.formatted(bookId, bookId));
        for (int retries = 0; ; retries++) {
            waitForTheMirror(bookId);
            HttpResponse<String> response = followingRedirects(uri, bookId);
            if (!BUSY.contains(response.statusCode())) {
                return body(response, bookId);
            }
            Duration wait = retryAfter(response).orElse(FIRST_WAIT.multipliedBy(1L << retries));
            if (retries == MAX_RETRIES || wait.compareTo(LONGEST_WAIT) > 0) {
                throw new DownloadException(FailureReason.NETWORK_ERROR,
                        "HTTP " + response.statusCode() + " for book " + bookId + ": the mirror is busy");
            }
            pauseTheMirror(wait);
        }
    }

    private void waitForTheMirror(int bookId) {
        Duration left = Duration.between(clock.instant(), pausedUntil.get());
        if (left.isPositive()) {
            try {
                pause.sleep(left);
            } catch (InterruptedException e) {
                restoreInterruption(e);
                throw new DownloadException(FailureReason.NETWORK_ERROR, "Interrupted waiting for the mirror, book " + bookId);
            }
        }
    }

    private void pauseTheMirror(Duration wait) {
        Instant until = clock.instant().plus(wait);
        pausedUntil.accumulateAndGet(until, (current, next) -> next.isAfter(current) ? next : current);
    }

    private HttpResponse<String> followingRedirects(URI uri, int bookId) {
        HttpResponse<String> response = send(uri, bookId);
        for (int redirects = 0; REDIRECTS.contains(response.statusCode()); redirects++) {
            if (redirects == MAX_REDIRECTS) {
                throw new DownloadException(FailureReason.NETWORK_ERROR, "Too many redirects for book " + bookId);
            }
            response = send(redirection(response, bookId), bookId);
        }
        return response;
    }

    private static URI redirection(HttpResponse<String> response, int bookId) {
        URI target = response.headers().firstValue("Location").flatMap(location -> resolved(response.uri(), location))
                .orElseThrow(() -> new DownloadException(FailureReason.NETWORK_ERROR,
                        "HTTP " + response.statusCode() + " without a valid Location for book " + bookId));
        if (!response.uri().getHost().equalsIgnoreCase(target.getHost())) {
            throw new DownloadException(FailureReason.NETWORK_ERROR,
                    "Book " + bookId + " was redirected to " + target.getHost() + ", which is not the mirror");
        }
        return target;
    }

    private static Optional<URI> resolved(URI base, String location) {
        try {
            return Optional.of(base.resolve(location));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private HttpResponse<String> send(URI uri, int bookId) {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).header("User-Agent", USER_AGENT).build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            restoreInterruption(e);
            throw new DownloadException(FailureReason.NETWORK_ERROR, "Could not download book " + bookId + ": " + e);
        }
    }

    private static void restoreInterruption(Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    /** The wait asked for by Retry-After, in seconds or as an HTTP date; empty without a readable one. */
    private Optional<Duration> retryAfter(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After").map(String::strip).flatMap(this::requestedWait);
    }

    private Optional<Duration> requestedWait(String retryAfter) {
        if (SECONDS.matcher(retryAfter).matches()) {
            return Optional.of(Duration.ofSeconds(Long.parseLong(retryAfter)));
        }
        try {
            Instant date = ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            Duration untilDate = Duration.between(clock.instant(), date);
            return Optional.of(untilDate.isNegative() ? Duration.ZERO : untilDate);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static String body(HttpResponse<String> response, int bookId) {
        return switch (response.statusCode()) {
            case 200 -> response.body();
            case 404 -> throw new DownloadException(FailureReason.NOT_FOUND, "Book " + bookId + " not found");
            default -> throw new DownloadException(FailureReason.NETWORK_ERROR, "HTTP " + response.statusCode() + " for book " + bookId);
        };
    }

    /** How a download waits for a busy mirror: {@link Thread#sleep(Duration)}, or a record of the waits in tests. */
    @FunctionalInterface
    interface Pause {
        void sleep(Duration duration) throws InterruptedException;
    }
}
