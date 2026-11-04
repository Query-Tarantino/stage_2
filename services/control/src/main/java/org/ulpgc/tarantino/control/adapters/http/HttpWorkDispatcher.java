package org.ulpgc.tarantino.control.adapters.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.http.InternalTasks;
import org.ulpgc.tarantino.common.json.TarantinoJson;
import org.ulpgc.tarantino.common.model.BookTask;
import org.ulpgc.tarantino.control.model.Stage;
import org.ulpgc.tarantino.control.ports.WorkDispatcher;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * {@code POST <TARANTINO_ORCHESTRATOR_TARGET>/internal/download} or {@code /internal/index} with the {@code BookTask}
 * JSON (SPEC §11.4), with a connect timeout of 1 s and a request timeout of 2 s.
 */
public class HttpWorkDispatcher implements WorkDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpWorkDispatcher.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);
    private static final int ACCEPTED = 202;
    private static final Map<Stage, String> PATHS = Map.of(Stage.DOWNLOAD, InternalTasks.DOWNLOAD, Stage.INDEX, InternalTasks.INDEX);

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final String target;

    public HttpWorkDispatcher(String target) {
        this.target = target;
    }

    @Override
    public boolean dispatch(Stage stage, BookTask task) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(target + PATHS.get(stage)))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(TarantinoJson.write(task)))
                .build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == ACCEPTED;
        } catch (IOException e) {
            LOGGER.warn("Could not dispatch book {} to {}: {}", task.bookId(), stage, e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
