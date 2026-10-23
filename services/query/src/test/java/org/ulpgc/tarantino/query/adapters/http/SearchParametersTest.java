package org.ulpgc.tarantino.query.adapters.http;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.http.ProblemException;
import org.ulpgc.tarantino.common.model.ProblemCode;
import org.ulpgc.tarantino.query.model.SearchQuery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchParametersTest {

    @Test
    void appliesTheDefaultPageWhenItsParametersAreAbsentOrBlank() {
        assertEquals(new SearchQuery("pride", 0, 20), SearchParameters.query("pride", null, null));
        assertEquals(new SearchQuery("pride", 0, 20), SearchParameters.query("pride", "", " "));
        assertEquals(new SearchQuery("pride", 40, 100), SearchParameters.query("pride", "40", "100"));
    }

    @Test
    void asksForAMissingOrBlankQ() {
        ProblemException missing = assertThrows(ProblemException.class, () -> SearchParameters.query(" ", null, null));

        assertEquals(ProblemCode.MISSING_PARAMETER, missing.code());
        assertEquals("q", missing.parameter());
    }

    @Test
    void rejectsAnOffsetOrLimitOutOfRangeOrNotNumeric() {
        assertInvalid("offset", () -> SearchParameters.query("pride", "-1", null));
        assertInvalid("offset", () -> SearchParameters.query("pride", "first", null));
        assertInvalid("limit", () -> SearchParameters.query("pride", null, "0"));
        assertInvalid("limit", () -> SearchParameters.query("pride", null, "101"));
    }

    @Test
    void checksQBeforeThePage() {
        assertEquals("q", assertThrows(ProblemException.class, () -> SearchParameters.query(null, "-1", "0")).parameter());
    }

    private static void assertInvalid(String parameter, Runnable query) {
        ProblemException invalid = assertThrows(ProblemException.class, query::run);
        assertEquals(ProblemCode.INVALID_PARAMETER, invalid.code());
        assertEquals(parameter, invalid.parameter());
    }
}
