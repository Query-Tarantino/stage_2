package org.ulpgc.tarantino.checks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Locale;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static org.ulpgc.tarantino.checks.ApiNames.CODE;
import static org.ulpgc.tarantino.checks.ApiNames.CONTENT_TYPE;
import static org.ulpgc.tarantino.checks.ApiNames.DETAIL;
import static org.ulpgc.tarantino.checks.ApiNames.STATUS;
import static org.ulpgc.tarantino.checks.ApiNames.TITLE;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertEquals;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertStatus;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertTrue;

/** nginx answers any path outside SPEC §6.1, on both entry ports, with a {@code 404} Problem Details without {@code code}. */
final class EntryPortChecks {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntryPortChecks.class);
    private static final String UNKNOWN_PATH = "/unknown-path";
    private static final String PROBLEM_JSON = "application/problem+json";

    private final CheckedClient client;
    private final String frontEndUrl;
    private final String operationalUrl;

    EntryPortChecks(CheckedClient client, String frontEndUrl, String operationalUrl) {
        this.client = client;
        this.frontEndUrl = frontEndUrl;
        this.operationalUrl = operationalUrl;
    }

    void check() throws IOException, InterruptedException {
        LOGGER.info("Checking nginx 404 Problem Details on unknown paths");
        checkUnknownPath(frontEndUrl + UNKNOWN_PATH);
        checkUnknownPath(operationalUrl + UNKNOWN_PATH);
    }

    private void checkUnknownPath(String url) throws IOException, InterruptedException {
        CheckedClient.Response response = client.getRaw(url);
        String notFound = "the 404 of " + url;
        assertStatus(HTTP_NOT_FOUND, response, "GET " + url);
        String contentType = response.header(CONTENT_TYPE);
        assertTrue(contentType != null, notFound + " has a Content-Type");
        assertTrue(contentType.toLowerCase(Locale.ROOT).startsWith(PROBLEM_JSON), notFound + " is " + PROBLEM_JSON);
        assertTrue(response.json() != null, notFound + " has a JSON body");
        client.validator().validateProblem(response.json());
        assertEquals(HTTP_NOT_FOUND, response.json().get(STATUS).asInt(), "the status field of " + notFound);
        assertTrue(response.json().has(TITLE), notFound + " has a title");
        assertTrue(response.json().has(DETAIL), notFound + " has a detail");
        assertTrue(!response.json().has(CODE), notFound + " has no code");
    }
}
