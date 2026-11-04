package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.ulpgc.tarantino.checks.Assertions.assertEquals;
import static org.ulpgc.tarantino.checks.Assertions.assertTrue;
import static org.ulpgc.tarantino.checks.SampleBooks.PRIDE_AND_PREJUDICE;
import static org.ulpgc.tarantino.checks.SearchResults.containsBook;

/**
 * {@code GET /search/filters} and {@code GET /search} (SPEC §6.3 and §6.4): words, each filter alone and combined,
 * pagination and the searches with no results, over the sample books.
 */
final class SearchChecks {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchChecks.class);
    private static final String SEARCH = "/search?q=";
    private static final String PRIDE = "pride";
    private static final String AUTHOR = "author";
    private static final String LANGUAGE = "language";
    private static final String YEAR = "year";
    private static final String AUSTEN = "Austen";
    private static final String ENGLISH = "en";
    private static final String RELEASE_YEAR = "1998";
    private static final List<String> SAMPLE_LANGUAGES = List.of("de", "fr");
    private static final int LARGE_OFFSET = 1_000_000_000;
    private static final int OK = 200;

    private final Api api;
    private final SearchTerms terms;

    SearchChecks(Api api, SearchTerms terms) {
        this.api = api;
        this.terms = terms;
    }

    void checkFilterSummary() throws IOException, InterruptedException {
        LOGGER.info("Checking /search/filters");
        JsonNode summary = ok("/search/filters");
        assertTrue(summary.has("languages"));
        assertTrue(summary.has("years"));
        assertTrue(summary.has("total_books"));
    }

    void check(List<JsonNode> sampleBooks) throws IOException, InterruptedException {
        LOGGER.info("Checking search endpoints");
        assertFindsPrideAndPrejudice(withQuery(SEARCH + PRIDE, PRIDE));
        assertFindsPrideAndPrejudice(withQuery(SEARCH + "pride%20prejudice", "pride prejudice"));
        assertFindsPrideAndPrejudice(withFilters(SEARCH + PRIDE + "&author=" + AUSTEN, Map.of(AUTHOR, AUSTEN)));
        assertFindsPrideAndPrejudice(withFilters(SEARCH + PRIDE + "&language=" + ENGLISH, Map.of(LANGUAGE, ENGLISH)));
        for (String language : SAMPLE_LANGUAGES) {
            checkLanguageOfASampleBook(sampleBooks, language);
        }
        assertNoResults(withFilters(SEARCH + PRIDE + "&language=zz", Map.of(LANGUAGE, "zz")));
        assertFindsPrideAndPrejudice(withFilters(SEARCH + PRIDE + "&year=" + RELEASE_YEAR, Map.of(YEAR, RELEASE_YEAR)));
        assertFindsPrideAndPrejudice(withFilters(SEARCH + PRIDE + "&author=" + AUSTEN + "&language=" + ENGLISH + "&year=" + RELEASE_YEAR,
                Map.of(AUTHOR, AUSTEN, LANGUAGE, ENGLISH, YEAR, RELEASE_YEAR)));
        checkPagination();
        checkSearchesWithoutResults();
    }

    /** A sample book in that language is found by a word of its own, with that filter; nothing to check without one. */
    private void checkLanguageOfASampleBook(List<JsonNode> sampleBooks, String language) throws IOException, InterruptedException {
        Optional<JsonNode> book = SearchTerms.firstIn(sampleBooks, language);
        if (book.isEmpty()) {
            return;
        }
        JsonNode answer = withFilters(SEARCH + terms.termFor(book.get()) + "&language=" + language, Map.of(LANGUAGE, language));
        assertTrue(answer.get("total").asInt() >= 1);
        assertTrue(containsBook(answer.get("results"), book.get().get("book_id").asInt()));
    }

    private void checkPagination() throws IOException, InterruptedException {
        JsonNode firstPage = page(0);
        JsonNode secondPage = page(1);
        assertEquals(firstPage.get("total").asInt(), secondPage.get("total").asInt());
        if (firstPage.get("total").asInt() >= 2) {
            assertTrue(firstResult(firstPage) != firstResult(secondPage));
        }
    }

    private JsonNode page(int offset) throws IOException, InterruptedException {
        JsonNode answer = ok(SEARCH + PRIDE + "&offset=" + offset + "&limit=1");
        assertEquals(offset, answer.get("offset").asInt());
        assertEquals(1, answer.get("limit").asInt());
        return answer;
    }

    private void checkSearchesWithoutResults() throws IOException, InterruptedException {
        JsonNode beyondTheLastPage = ok(SEARCH + PRIDE + "&offset=" + LARGE_OFFSET);
        assertEquals(LARGE_OFFSET, beyondTheLastPage.get("offset").asInt());
        assertEquals(0, beyondTheLastPage.get("results").size());
        assertNoResults(ok(SEARCH + "the%20and"));
        assertNoResults(ok(SEARCH + "pyromaniac%20liliput"));
    }

    private JsonNode withQuery(String path, String query) throws IOException, InterruptedException {
        JsonNode answer = ok(path);
        assertEquals(query, answer.get("query").asText());
        return answer;
    }

    private JsonNode withFilters(String path, Map<String, String> filters) throws IOException, InterruptedException {
        JsonNode answer = ok(path);
        filters.forEach((filter, value) -> assertEquals(value, answer.get("filters").get(filter).asText()));
        return answer;
    }

    private JsonNode ok(String path) throws IOException, InterruptedException {
        CheckedClient.Response response = api.frontEnd(path);
        assertEquals(OK, response.status());
        return response.json();
    }

    private static void assertFindsPrideAndPrejudice(JsonNode answer) {
        assertTrue(answer.get("total").asInt() >= 1);
        assertTrue(containsBook(answer.get("results"), PRIDE_AND_PREJUDICE));
    }

    private static void assertNoResults(JsonNode answer) {
        assertEquals(0, answer.get("total").asInt());
        assertEquals(0, answer.get("results").size());
    }

    private static int firstResult(JsonNode page) {
        return page.get("results").get(0).get("book_id").asInt();
    }
}
