package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiValidatorTest {

    private static OpenApiValidator validator;

    @BeforeAll
    static void setUp() throws IOException {
        validator = OpenApiValidator.load(findOpenApiPath());
    }

    private static Path findOpenApiPath() {
        List<Path> candidates = List.of(
                Paths.get("api/openapi.yaml"),
                Paths.get("../api/openapi.yaml"),
                Paths.get("../../api/openapi.yaml")
        );
        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find api/openapi.yaml"));
    }

    @Test
    void validatesValidSearchResponse() {
        String body = """
                {
                  "query": "pride",
                  "filters": {},
                  "total": 1,
                  "offset": 0,
                  "limit": 20,
                  "results": [
                    {
                      "book_id": 1342,
                      "title": "Pride and Prejudice",
                      "author": "Jane Austen",
                      "language": "en",
                      "year": 1998
                    }
                  ]
                }
                """;
        Map<String, List<String>> headers = Map.of("Content-Type", List.of("application/json"));
        assertDoesNotThrow(() -> validator.validateResponse("GET", "/search?q=pride", 200, headers, body));
    }

    @Test
    void failsOnAdditionalProperties() {
        String body = """
                {
                  "query": "pride",
                  "filters": {},
                  "total": 1,
                  "offset": 0,
                  "limit": 20,
                  "results": [],
                  "unexpected_extra_field": true
                }
                """;
        Map<String, List<String>> headers = Map.of("Content-Type", List.of("application/json"));
        assertThrows(AssertionError.class, () -> validator.validateResponse("GET", "/search?q=pride", 200, headers, body));
    }

    @Test
    void failsOnMinimumViolation() {
        String body = """
                {
                  "query": "pride",
                  "filters": {},
                  "total": 1,
                  "offset": -1,
                  "limit": 20,
                  "results": []
                }
                """;
        Map<String, List<String>> headers = Map.of("Content-Type", List.of("application/json"));
        assertThrows(AssertionError.class, () -> validator.validateResponse("GET", "/search?q=pride", 200, headers, body));
    }

    @Test
    void failsOnMaximumViolation() {
        String body = """
                {
                  "query": "pride",
                  "filters": {},
                  "total": 1,
                  "offset": 0,
                  "limit": 101,
                  "results": []
                }
                """;
        Map<String, List<String>> headers = Map.of("Content-Type", List.of("application/json"));
        assertThrows(AssertionError.class, () -> validator.validateResponse("GET", "/search?q=pride", 200, headers, body));
    }

    @Test
    void failsOnMinItemsViolation() {
        String body = """
                {
                  "status": "DOWN",
                  "down": []
                }
                """;
        Map<String, List<String>> headers = Map.of(
                "Content-Type", List.of("application/json"),
                "Retry-After", List.of("5")
        );
        assertThrows(AssertionError.class, () -> validator.validateResponse("GET", "/health", 503, headers, body));
    }

    @Test
    void failsOnMaxItemsViolation() {
        List<String> items = IntStream.range(0, 101)
                .mapToObj(index -> "{\"book_id\": 1, \"title\": null, \"author\": null, \"language\": null, \"year\": null}")
                .toList();
        String body = "{\"query\":\"q\",\"filters\":{},\"total\":101,\"offset\":0,\"limit\":100,\"results\":[" + String.join(",", items) + "]}";
        Map<String, List<String>> headers = Map.of("Content-Type", List.of("application/json"));
        assertThrows(AssertionError.class, () -> validator.validateResponse("GET", "/search?q=q", 200, headers, body));
    }

    @Test
    void failsOnPatternMismatch() {
        String body = """
                {
                  "query": "pride",
                  "filters": {},
                  "total": 1,
                  "offset": 0,
                  "limit": 20,
                  "results": [
                    {
                      "book_id": 1342,
                      "title": "Pride and Prejudice",
                      "author": "Jane Austen",
                      "language": "english",
                      "year": 1998
                    }
                  ]
                }
                """;
        Map<String, List<String>> headers = Map.of("Content-Type", List.of("application/json"));
        assertThrows(AssertionError.class, () -> validator.validateResponse("GET", "/search?q=pride", 200, headers, body));
    }

    @Test
    void validatesIngestAcceptedWithLocation() {
        String body = """
                {
                  "book_id": 1342,
                  "status": "QUEUED",
                  "attempts": 0,
                  "last_error": null,
                  "updated_at": "2026-10-21T10:00:00Z"
                }
                """;
        Map<String, List<String>> headers = Map.of(
                "Content-Type", List.of("application/json"),
                "Location", List.of("/ingest/status/1342")
        );
        assertDoesNotThrow(() -> validator.validateResponse("POST", "/ingest/1342", 202, headers, body));
    }

    @Test
    void failsWhenRequiredHeaderIsMissing() {
        String body = """
                {
                  "book_id": 1342,
                  "status": "QUEUED",
                  "attempts": 0,
                  "last_error": null,
                  "updated_at": "2026-10-21T10:00:00Z"
                }
                """;
        Map<String, List<String>> headers = Map.of("Content-Type", List.of("application/json"));
        assertThrows(AssertionError.class, () -> validator.validateResponse("POST", "/ingest/1342", 202, headers, body));
    }

    @Test
    void validatesProblemDetailsErrorResponse() {
        String body = """
                {
                  "type": "about:blank",
                  "title": "Bad Request",
                  "status": 400,
                  "code": "INVALID_PARAMETER",
                  "parameter": "limit",
                  "detail": "limit must be between 1 and 100"
                }
                """;
        Map<String, List<String>> headers = Map.of("Content-Type", List.of("application/problem+json"));
        assertDoesNotThrow(() -> validator.validateResponse("GET", "/search?q=pride&limit=0", 400, headers, body));
    }

    @Test
    void failsOnUndeclaredStatusCode() {
        Map<String, List<String>> headers = Map.of("Content-Type", List.of("application/json"));
        assertThrows(AssertionError.class, () -> validator.validateResponse("GET", "/search?q=pride", 418, headers, "{}"));
    }

    @Test
    void validatesNginxNotFoundProblemDetails() {
        String body = """
                {
                  "type": "about:blank",
                  "title": "Not Found",
                  "status": 404,
                  "detail": "The requested path does not exist"
                }
                """;
        JsonNode jsonNode = assertDoesNotThrow(() -> TarantinoJson.mapper().readTree(body));
        assertDoesNotThrow(() -> validator.validateProblem(jsonNode));
        assertTrue(!jsonNode.has("code"));
    }

    @Test
    void verifiesExpectedJsonStructureAndSchemas() throws IOException {
        Path expectedPath = findFile("checks/expected.json");
        JsonNode root = TarantinoJson.mapper().readTree(expectedPath.toFile());
        assertTrue(root.has("total_books"));
        assertEquals(20, root.get("total_books").asInt());
        assertTrue(root.has("sample_ids") && root.get("sample_ids").isArray());
        assertEquals(20, root.get("sample_ids").size());

        assertTrue(root.has("searches") && root.get("searches").isObject());
        JsonNode searches = root.get("searches");
        List<String> requiredCases = List.of(
                "single_word", "multi_word", "filter_author", "filter_language", "filter_year",
                "combined_filter", "pagination_page0", "pagination_page1", "pagination_large_offset",
                "stopwords_only", "empty_results"
        );
        for (String testCase : requiredCases) {
            assertTrue(searches.has(testCase), "Missing search case: " + testCase);
            JsonNode caseNode = searches.get(testCase);
            assertTrue(caseNode.has("path"));
            assertTrue(caseNode.has("total"));
            assertTrue(caseNode.has("book_ids") && caseNode.get("book_ids").isArray());
        }

        assertTrue(root.has("books") && root.get("books").isObject());
        JsonNode books = root.get("books");
        assertEquals(20, books.size());
        for (JsonNode idNode : root.get("sample_ids")) {
            String bookId = String.valueOf(idNode.asInt());
            assertTrue(books.has(bookId), "Missing book metadata for " + bookId);
            JsonNode book = books.get(bookId);
            assertTrue(book.has("book_id"));
            assertTrue(book.has("title"));
            assertTrue(book.has("author"));
            assertTrue(book.has("language"));
            assertTrue(book.has("year"));
        }
    }

    private static Path findFile(String relativePath) {
        List<Path> candidates = List.of(
                Paths.get(relativePath),
                Paths.get("../" + relativePath),
                Paths.get("../../" + relativePath)
        );
        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find file: " + relativePath));
    }
}
