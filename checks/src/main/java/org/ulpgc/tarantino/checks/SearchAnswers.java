package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.ulpgc.tarantino.checks.ApiNames.FILTERS;
import static org.ulpgc.tarantino.checks.ApiNames.RESULTS;
import static org.ulpgc.tarantino.checks.ApiNames.TOTAL;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertEquals;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertStatus;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertTrue;
import static org.ulpgc.tarantino.checks.SampleBooks.PRIDE_AND_PREJUDICE;

/** The {@code 200} answer of a {@code GET /search} on the front-end port (SPEC §6.3), and what the search checks expect of it. */
final class SearchAnswers {

    private final CheckedClient client;
    private final String frontEndUrl;

    SearchAnswers(CheckedClient client, String frontEndUrl) {
        this.client = client;
        this.frontEndUrl = frontEndUrl;
    }

    JsonNode answer(String path) throws IOException, InterruptedException {
        CheckedClient.Response response = client.get(frontEndUrl + path);
        assertStatus(HTTP_OK, response, "GET " + path);
        return response.json();
    }

    JsonNode findsPrideAndPrejudice(String path) throws IOException, InterruptedException {
        JsonNode answer = answer(path);
        assertTrue(answer.get(TOTAL).asInt() >= 1, path + " finds at least one book");
        assertTrue(SearchResults.containsBook(answer.get(RESULTS), PRIDE_AND_PREJUDICE), path + " finds book " + PRIDE_AND_PREJUDICE);
        return answer;
    }

    JsonNode findsNothing(String path) throws IOException, InterruptedException {
        JsonNode answer = answer(path);
        assertEquals(0, answer.get(TOTAL).asInt(), "the total of " + path);
        assertEquals(0, answer.get(RESULTS).size(), "the results of " + path);
        return answer;
    }

    static void assertFilter(String path, JsonNode answer, String filter, String expected) {
        assertEquals(expected, answer.get(FILTERS).get(filter).asText(), "the " + filter + " filter of " + path);
    }
}
