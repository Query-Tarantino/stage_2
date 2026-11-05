package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.ulpgc.tarantino.checks.ApiNames.AUTHOR;
import static org.ulpgc.tarantino.checks.ApiNames.BOOK_ID;
import static org.ulpgc.tarantino.checks.ApiNames.LANGUAGE;
import static org.ulpgc.tarantino.checks.ApiNames.LANGUAGES;
import static org.ulpgc.tarantino.checks.ApiNames.QUERY;
import static org.ulpgc.tarantino.checks.ApiNames.RESULTS;
import static org.ulpgc.tarantino.checks.ApiNames.SEARCH;
import static org.ulpgc.tarantino.checks.ApiNames.SEARCH_FILTERS;
import static org.ulpgc.tarantino.checks.ApiNames.TOTAL;
import static org.ulpgc.tarantino.checks.ApiNames.TOTAL_BOOKS;
import static org.ulpgc.tarantino.checks.ApiNames.YEAR;
import static org.ulpgc.tarantino.checks.ApiNames.YEARS;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertEquals;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertStatus;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertTrue;
import static org.ulpgc.tarantino.checks.SampleBooks.PRIDE_AND_PREJUDICE_YEAR;
import static org.ulpgc.tarantino.checks.SearchAnswers.assertFilter;

/** {@code GET /search/filters} and the word and filter searches of {@code GET /search} (SPEC §6.3 and §6.4). */
final class SearchChecks {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchChecks.class);
    private static final String AUSTEN = "Austen";
    private static final String ENGLISH = "en";
    private static final String NO_LANGUAGE = "zz";
    private static final List<String> OTHER_SAMPLE_LANGUAGES = List.of("de", "fr");
    private static final String PRIDE = SEARCH + "?q=pride";
    private static final String TWO_WORDS = PRIDE + "%20prejudice";

    private final CheckedClient client;
    private final SearchAnswers answers;
    private final SearchTerms terms;
    private final String frontEndUrl;

    SearchChecks(CheckedClient client, SearchAnswers answers, SearchTerms terms, String frontEndUrl) {
        this.client = client;
        this.answers = answers;
        this.terms = terms;
        this.frontEndUrl = frontEndUrl;
    }

    void checkFilters() throws IOException, InterruptedException {
        LOGGER.info("Checking /search/filters");
        CheckedClient.Response response = client.get(frontEndUrl + SEARCH_FILTERS);
        assertStatus(HTTP_OK, response, "GET " + SEARCH_FILTERS);
        List.of(LANGUAGES, YEARS, TOTAL_BOOKS).forEach(field ->
                assertTrue(response.json().has(field), SEARCH_FILTERS + " has " + field));
    }

    void check(List<JsonNode> sampleBooks) throws IOException, InterruptedException {
        LOGGER.info("Checking search endpoints");
        assertEquals("pride", answers.findsPrideAndPrejudice(PRIDE).get(QUERY).asText(), "the query of " + PRIDE);
        assertEquals("pride prejudice", answers.findsPrideAndPrejudice(TWO_WORDS).get(QUERY).asText(),
                "the query of " + TWO_WORDS);
        String byAuthor = PRIDE + "&author=" + AUSTEN;
        assertFilter(byAuthor, answers.findsPrideAndPrejudice(byAuthor), AUTHOR, AUSTEN);
        String byLanguage = PRIDE + "&language=" + ENGLISH;
        assertFilter(byLanguage, answers.findsPrideAndPrejudice(byLanguage), LANGUAGE, ENGLISH);
        for (String language : OTHER_SAMPLE_LANGUAGES) {
            checkLanguageFilter(sampleBooks, language);
        }
        String byMissingLanguage = PRIDE + "&language=" + NO_LANGUAGE;
        assertFilter(byMissingLanguage, answers.findsNothing(byMissingLanguage), LANGUAGE, NO_LANGUAGE);
        checkYearFilter(byAuthor + "&language=" + ENGLISH);
    }

    private void checkYearFilter(String byAuthorAndLanguage) throws IOException, InterruptedException {
        String year = String.valueOf(PRIDE_AND_PREJUDICE_YEAR);
        String byYear = PRIDE + "&year=" + year;
        assertFilter(byYear, answers.findsPrideAndPrejudice(byYear), YEAR, year);
        String combined = byAuthorAndLanguage + "&year=" + year;
        JsonNode answer = answers.findsPrideAndPrejudice(combined);
        assertFilter(combined, answer, AUTHOR, AUSTEN);
        assertFilter(combined, answer, LANGUAGE, ENGLISH);
        assertFilter(combined, answer, YEAR, year);
    }

    private void checkLanguageFilter(List<JsonNode> sampleBooks, String language) throws IOException, InterruptedException {
        Optional<JsonNode> book = SearchTerms.bookIn(sampleBooks, language);
        if (book.isEmpty()) {
            return;
        }
        int bookId = book.get().get(BOOK_ID).asInt();
        String path = SEARCH + "?q=" + terms.termOf(book.get()) + "&language=" + language;
        JsonNode answer = answers.answer(path);
        assertFilter(path, answer, LANGUAGE, language);
        assertTrue(answer.get(TOTAL).asInt() >= 1, path + " finds at least one book");
        assertTrue(SearchResults.containsBook(answer.get(RESULTS), bookId), path + " finds book " + bookId);
    }
}
