package org.ulpgc.tarantino.checks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Locale;

import static org.ulpgc.tarantino.checks.Assertions.assertEquals;
import static org.ulpgc.tarantino.checks.Assertions.assertTrue;

/** A path that neither nginx entry port routes answers {@code 404} as Problem Details without {@code code} (SPEC §6.6). */
final class NginxChecks {

    private static final Logger LOGGER = LoggerFactory.getLogger(NginxChecks.class);
    private static final String UNKNOWN_PATH = "/unknown-path";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final int NOT_FOUND = 404;

    private final Api api;

    NginxChecks(Api api) {
        this.api = api;
    }

    void check() throws IOException, InterruptedException {
        LOGGER.info("Checking nginx 404 Problem Details on unknown paths");
        checkUnknownPath(api.frontEndUrl() + UNKNOWN_PATH);
        checkUnknownPath(api.operationalUrl() + UNKNOWN_PATH);
    }

    private void checkUnknownPath(String url) throws IOException, InterruptedException {
        CheckedClient.Response response = api.client().getRaw(url);
        assertEquals(NOT_FOUND, response.status());
        String contentType = response.header(CONTENT_TYPE);
        assertTrue(contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith(PROBLEM_JSON));
        assertTrue(response.json() != null);
        api.client().validator().validateProblem(response.json());
        assertEquals(NOT_FOUND, response.json().get("status").asInt());
        assertTrue(response.json().has("title"));
        assertTrue(response.json().has("detail"));
        assertTrue(!response.json().has("code"));
    }
}
