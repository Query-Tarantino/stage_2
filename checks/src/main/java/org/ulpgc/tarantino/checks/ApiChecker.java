package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class ApiChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiChecker.class);
    private static final int MAX_WAIT_SECONDS = 60;

    private final CheckedClient client;
    private final String frontEndUrl;
    private final String operationalUrl;

    public ApiChecker(CheckedClient client, String frontEndUrl, String operationalUrl) {
        this.client = client;
        this.frontEndUrl = trimSlash(frontEndUrl);
        this.operationalUrl = trimSlash(operationalUrl);
    }

    public void checkAll(List<Integer> sampleIds) throws IOException, InterruptedException {
        LOGGER.info("Starting API conformance checks");
        checkHealth();
        checkSearchParametersOrder();
        checkSearchEndpoints();
        checkBooksEndpoints();
        checkFiltersSummary();
        checkOperationalEndpoints(sampleIds);
        LOGGER.info("All API conformance checks passed successfully");
    }

    public void checkSearchEndpoints() throws IOException, InterruptedException {
        LOGGER.info("Checking search endpoints");
        CheckedClient.Response single = client.get(frontEndUrl + "/search?q=pride");
        assertEquals(200, single.status());
        assertEquals("pride", single.json().get("query").asText());

        CheckedClient.Response multiple = client.get(frontEndUrl + "/search?q=pride%20prejudice");
        assertEquals(200, multiple.status());

        CheckedClient.Response stopwords = client.get(frontEndUrl + "/search?q=the%20and");
        assertEquals(200, stopwords.status());
        assertEquals(0, stopwords.json().get("total").asInt());

        CheckedClient.Response empty = client.get(frontEndUrl + "/search?q=pyromaniac%20liliput");
        assertEquals(200, empty.status());
        assertEquals(0, empty.json().get("total").asInt());

        CheckedClient.Response withAuthor = client.get(frontEndUrl + "/search?q=pride&author=Austen");
        assertEquals(200, withAuthor.status());

        CheckedClient.Response withLanguage = client.get(frontEndUrl + "/search?q=pride&language=en");
        assertEquals(200, withLanguage.status());

        CheckedClient.Response withYear = client.get(frontEndUrl + "/search?q=pride&year=1998");
        assertEquals(200, withYear.status());

        CheckedClient.Response combined = client.get(frontEndUrl + "/search?q=pride&author=Austen&language=en&year=1998");
        assertEquals(200, combined.status());

        CheckedClient.Response page1 = client.get(frontEndUrl + "/search?q=pride&offset=0&limit=1");
        assertEquals(200, page1.status());
        assertEquals(0, page1.json().get("offset").asInt());
        assertEquals(1, page1.json().get("limit").asInt());

        CheckedClient.Response largeOffset = client.get(frontEndUrl + "/search?q=pride&offset=1000000000");
        assertEquals(200, largeOffset.status());
        assertEquals(1000000000, largeOffset.json().get("offset").asInt());
    }

    public void checkSearchParametersOrder() throws IOException, InterruptedException {
        LOGGER.info("Checking parameter validation in order");
        assertProblem(frontEndUrl + "/search", 400, "MISSING_PARAMETER", "q");
        assertProblem(frontEndUrl + "/search?q=", 400, "MISSING_PARAMETER", "q");
        assertProblem(frontEndUrl + "/search?q=pride&author=...!", 400, "INVALID_PARAMETER", "author");
        assertProblem(frontEndUrl + "/search?q=pride&language=eng", 400, "INVALID_PARAMETER", "language");
        assertProblem(frontEndUrl + "/search?q=pride&year=98", 400, "INVALID_PARAMETER", "year");
        assertProblem(frontEndUrl + "/search?q=pride&offset=-1", 400, "INVALID_PARAMETER", "offset");
        assertProblem(frontEndUrl + "/search?q=pride&limit=0", 400, "INVALID_PARAMETER", "limit");
        assertProblem(frontEndUrl + "/search?q=pride&limit=101", 400, "INVALID_PARAMETER", "limit");
        assertProblem(frontEndUrl + "/search?q=&author=...!&language=bad&year=bad&offset=-1&limit=0", 400, "MISSING_PARAMETER", "q");
        assertProblem(frontEndUrl + "/search?q=pride&author=...!&language=bad&year=bad&offset=-1&limit=0", 400, "INVALID_PARAMETER", "author");
    }

    public void checkBooksEndpoints() throws IOException, InterruptedException {
        LOGGER.info("Checking /books endpoints");
        CheckedClient.Response notFound = client.get(frontEndUrl + "/books/999999");
        assertEquals(404, notFound.status());
        assertEquals("BOOK_NOT_FOUND", notFound.json().get("code").asText());

        assertProblem(frontEndUrl + "/books/0", 400, "INVALID_PARAMETER", "book_id");
        assertProblem(frontEndUrl + "/books/-5", 400, "INVALID_PARAMETER", "book_id");
    }

    public void checkFiltersSummary() throws IOException, InterruptedException {
        LOGGER.info("Checking /search/filters");
        CheckedClient.Response response = client.get(frontEndUrl + "/search/filters");
        assertEquals(200, response.status());
        JsonNode json = response.json();
        assertTrue(json.has("languages"));
        assertTrue(json.has("years"));
        assertTrue(json.has("total_books"));
    }

    public void checkOperationalEndpoints(List<Integer> sampleIds) throws IOException, InterruptedException {
        LOGGER.info("Checking operational endpoints");
        CheckedClient.Response list = client.get(operationalUrl + "/ingest/list");
        assertEquals(200, list.status());
        assertTrue(list.json().has("books"));

        CheckedClient.Response indexStatus = client.get(operationalUrl + "/index/status");
        assertEquals(200, indexStatus.status());
        assertTrue(indexStatus.json().has("generation"));
        assertTrue(indexStatus.json().has("books_indexed"));

        CheckedClient.Response controlStatus = client.get(operationalUrl + "/control/status");
        assertEquals(200, controlStatus.status());
        assertTrue(controlStatus.json().has("books"));
        assertTrue(controlStatus.json().has("queues"));

        if (!sampleIds.isEmpty()) {
            checkIngestionAndIdempotency(sampleIds.getFirst());
        }
    }

    public void checkHealth() throws IOException, InterruptedException {
        LOGGER.info("Checking /health on service ports");
        List<String> ports = List.of("7001", "7002", "7003", "7004");
        for (String port : ports) {
            CheckedClient.Response response = client.get("http://localhost:" + port + "/health");
            assertTrue(response.status() == 200 || response.status() == 503);
            assertTrue(response.json().has("status"));
        }
    }

    private void checkIngestionAndIdempotency(int bookId) throws IOException, InterruptedException {
        LOGGER.info("Checking ingestion and idempotency for book {}", bookId);
        CheckedClient.Response ingest = client.post(operationalUrl + "/ingest/" + bookId, "");
        assertTrue(ingest.status() == 202 || ingest.status() == 200);
        String location = ingest.header("Location");
        if (location != null) {
            waitForPublished(operationalUrl + location);
        }
        CheckedClient.Response repeated = client.post(operationalUrl + "/ingest/" + bookId, "");
        assertTrue(repeated.status() == 200 || repeated.status() == 202);

        CheckedClient.Response update = client.post(operationalUrl + "/index/update/" + bookId, "");
        assertTrue(update.status() == 200 || update.status() == 202);
    }

    private void waitForPublished(String statusUrl) throws IOException, InterruptedException {
        for (int i = 0; i < MAX_WAIT_SECONDS; i++) {
            CheckedClient.Response response = client.get(statusUrl);
            String status = response.json().get("status").asText();
            if ("PUBLISHED".equals(status) || "FAILED".equals(status)) {
                return;
            }
            Thread.sleep(1000);
        }
    }

    private void assertProblem(String url, int expectedStatus, String expectedCode, String expectedParam) throws IOException, InterruptedException {
        CheckedClient.Response response = client.get(url);
        assertEquals(expectedStatus, response.status());
        assertEquals(expectedCode, response.json().get("code").asText());
        if (expectedParam != null && response.json().has("parameter")) {
            assertEquals(expectedParam, response.json().get("parameter").asText());
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Assertion condition was false");
        }
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
