package org.ulpgc.tarantino.query.adapters.http;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.http.ProblemException;
import org.ulpgc.tarantino.common.model.ProblemCode;
import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.query.model.SearchQuery;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchParametersTest {

    private final Tokenizer tokenizer = new Tokenizer(Set.of("the", "and"));

    @Test
    void appliesTheDefaultPageWhenItsParametersAreAbsentOrBlank() {
        assertEquals(new SearchQuery("pride", 0, 20), SearchParameters.query("pride", null, null));
        assertEquals(new SearchQuery("pride", 0, 20), SearchParameters.query("pride", "", " "));
        assertEquals(new SearchQuery("pride", 40, 100), SearchParameters.query("pride", "40", "100"));
        assertEquals(new SearchQuery("pride", 1000000000, 20), SearchParameters.query("pride", "1000000000", null));
    }

    @Test
    void parsesValidFilters() {
        SearchQuery query = SearchParameters.query(tokenizer, "pride", "Jane Austen", "en", "1998", "0", "20");

        assertEquals("pride", query.q());
        assertEquals("Jane Austen", query.author());
        assertEquals(List.of("jane", "austen"), query.authorTerms());
        assertEquals("en", query.language());
        assertEquals(1998, query.year());
    }

    @Test
    void treatsEmptyOrBlankOptionalFiltersAsAbsent() {
        SearchQuery query = SearchParameters.query(tokenizer, "pride", "", "  ", null, null, null);

        assertNull(query.author());
        assertEquals(List.of(), query.authorTerms());
        assertNull(query.language());
        assertNull(query.year());
    }

    @Test
    void asksForAMissingOrBlankQ() {
        ProblemException missing = assertThrows(ProblemException.class,
                () -> SearchParameters.query(tokenizer, " ", null, null, null, null, null));

        assertEquals(ProblemCode.MISSING_PARAMETER, missing.code());
        assertEquals("q", missing.parameter());
    }

    @Test
    void rejectsAuthorWhenNoWordsAreLeftAfterTokenization() {
        assertInvalid("author", () -> SearchParameters.query(tokenizer, "pride", "...!?", null, null, null, null));
        assertInvalid("author", () -> SearchParameters.query(tokenizer, "pride", "the and", null, null, null, null));
    }

    @Test
    void rejectsMalformedLanguageCode() {
        assertInvalid("language", () -> SearchParameters.query(tokenizer, "pride", null, "EN", null, null, null));
        assertInvalid("language", () -> SearchParameters.query(tokenizer, "pride", null, "eng", null, null, null));
        assertInvalid("language", () -> SearchParameters.query(tokenizer, "pride", null, "e1", null, null, null));
        assertInvalid("language", () -> SearchParameters.query(tokenizer, "pride", null, "e", null, null, null));
    }

    @Test
    void rejectsMalformedYear() {
        assertInvalid("year", () -> SearchParameters.query(tokenizer, "pride", null, null, "98", null, null));
        assertInvalid("year", () -> SearchParameters.query(tokenizer, "pride", null, null, "19981", null, null));
        assertInvalid("year", () -> SearchParameters.query(tokenizer, "pride", null, null, "abcd", null, null));
    }

    @Test
    void rejectsAnOffsetOrLimitOutOfRangeOrNotNumeric() {
        assertInvalid("offset", () -> SearchParameters.query(tokenizer, "pride", null, null, null, "-1", null));
        assertInvalid("offset", () -> SearchParameters.query(tokenizer, "pride", null, null, null, "first", null));
        assertInvalid("offset", () -> SearchParameters.query(tokenizer, "pride", null, null, null, "99999999999999999999", null));
        assertInvalid("limit", () -> SearchParameters.query(tokenizer, "pride", null, null, null, null, "0"));
        assertInvalid("limit", () -> SearchParameters.query(tokenizer, "pride", null, null, null, null, "101"));
        assertInvalid("limit", () -> SearchParameters.query(tokenizer, "pride", null, null, null, null, "99999999999999999999"));
    }

    @Test
    void checksParametersInTheSpecifiedOrder() {
        assertParameter("q", () -> SearchParameters.query(tokenizer, null, "...", "bad", "bad", "-1", "0"));
        assertParameter("author", () -> SearchParameters.query(tokenizer, "pride", "...", "bad", "bad", "-1", "0"));
        assertParameter("language", () -> SearchParameters.query(tokenizer, "pride", "Austen", "bad", "bad", "-1", "0"));
        assertParameter("year", () -> SearchParameters.query(tokenizer, "pride", "Austen", "en", "bad", "-1", "0"));
        assertParameter("offset", () -> SearchParameters.query(tokenizer, "pride", "Austen", "en", "1998", "-1", "0"));
        assertParameter("limit", () -> SearchParameters.query(tokenizer, "pride", "Austen", "en", "1998", "0", "0"));
    }

    private static void assertInvalid(String parameter, Runnable query) {
        ProblemException invalid = assertThrows(ProblemException.class, query::run);
        assertEquals(ProblemCode.INVALID_PARAMETER, invalid.code());
        assertEquals(parameter, invalid.parameter());
    }

    private static void assertParameter(String parameter, Runnable query) {
        ProblemException problem = assertThrows(ProblemException.class, query::run);
        assertEquals(parameter, problem.parameter());
    }
}
