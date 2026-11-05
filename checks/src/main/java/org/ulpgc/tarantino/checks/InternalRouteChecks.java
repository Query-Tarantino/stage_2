package org.ulpgc.tarantino.checks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.common.http.InternalTasks;

import java.io.IOException;
import java.util.List;

import static java.net.HttpURLConnection.HTTP_ACCEPTED;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static org.ulpgc.tarantino.checks.ApiNames.BOOK_ID;
import static org.ulpgc.tarantino.checks.ApiNames.PARAMETER;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertTrue;

/**
 * The internal routes of mode {@code orchestrator} (SPEC §6.1 and §11.4), asked from inside the {@code control}
 * container, which calls them, because nginx's port 8082 is not published to the host. {@code POST /internal/download}
 * and {@code POST /internal/index} take a {@code BookTask} with {@code 202} and reject a malformed body or
 * {@code book_id} with {@code 400}, and {@code POST /internal/snapshot/refresh} of a {@code query} replica answers
 * {@code 202}. The book sent is one the checks already published, so its claim fails and nothing changes.
 */
final class InternalRouteChecks {

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalRouteChecks.class);
    private static final String CALLER = "control";
    private static final String INTERNAL_PORT = "http://nginx:8082";
    private static final String QUERY_REPLICA = "http://query:" + TarantinoService.QUERY.defaultPort();
    private static final String SNAPSHOT_REFRESH = "/internal/snapshot/refresh";
    private static final String BODY = "body";
    private static final String NOT_JSON = "pride";
    private static final int NOT_A_BOOK_ID = 0;
    private static final String ENQUEUED_AT = "2026-10-21T10:05:00Z";

    private final InsideCompose insideCompose;

    InternalRouteChecks(CheckedClient client) {
        this.insideCompose = new InsideCompose(client);
    }

    void check(int publishedBookId) throws IOException, InterruptedException {
        LOGGER.info("Checking the internal routes of mode orchestrator from inside the control container");
        for (String route : List.of(InternalTasks.DOWNLOAD, InternalTasks.INDEX)) {
            accepts(route, publishedBookId);
            rejects(route, NOT_JSON, BODY);
            rejects(route, task(NOT_A_BOOK_ID), BOOK_ID);
        }
        CheckedClient.Response refresh = insideCompose.post(CALLER, QUERY_REPLICA + SNAPSHOT_REFRESH, SNAPSHOT_REFRESH);
        assertTrue(refresh.status() == HTTP_ACCEPTED, "POST " + SNAPSHOT_REFRESH + " answers 202");
    }

    private void accepts(String route, int bookId) throws IOException, InterruptedException {
        CheckedClient.Response response = insideCompose.postJson(CALLER, INTERNAL_PORT + route, task(bookId), route);
        assertTrue(response.status() == HTTP_ACCEPTED, "POST " + route + " accepts a BookTask with 202");
    }

    private void rejects(String route, String body, String parameter) throws IOException, InterruptedException {
        CheckedClient.Response response = insideCompose.postJson(CALLER, INTERNAL_PORT + route, body, route);
        assertTrue(response.status() == HTTP_BAD_REQUEST && parameter.equals(response.json().path(PARAMETER).asText()),
                "POST " + route + " rejects a bad " + parameter + " with 400 naming it");
    }

    private static String task(int bookId) {
        return "{\"book_id\": " + bookId + ", \"enqueued_at\": \"" + ENQUEUED_AT + "\"}";
    }
}
