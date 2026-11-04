package org.ulpgc.tarantino.checks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.model.ProblemCode;

import java.io.IOException;
import java.util.List;

import static org.ulpgc.tarantino.checks.Assertions.assertProblem;

/**
 * The parameters of {@code GET /search} (SPEC §6.3): each invalid value gives {@code 400}, and when several are
 * invalid, the first one in the order {@code q}, {@code author}, {@code language}, {@code year}, {@code offset},
 * {@code limit} is the one named.
 */
final class SearchParameterChecks {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchParameterChecks.class);
    private static final ProblemCode MISSING = ProblemCode.MISSING_PARAMETER;
    private static final ProblemCode INVALID = ProblemCode.INVALID_PARAMETER;
    private static final List<InvalidSearch> INVALID_SEARCHES = List.of(
            new InvalidSearch("", MISSING, "q"),
            new InvalidSearch("?q=", MISSING, "q"),
            new InvalidSearch("?q=%20", MISSING, "q"),
            new InvalidSearch("?q=pride&author=...!", INVALID, "author"),
            new InvalidSearch("?q=pride&language=eng", INVALID, "language"),
            new InvalidSearch("?q=pride&language=E", INVALID, "language"),
            new InvalidSearch("?q=pride&language=12", INVALID, "language"),
            new InvalidSearch("?q=pride&year=98", INVALID, "year"),
            new InvalidSearch("?q=pride&year=19980", INVALID, "year"),
            new InvalidSearch("?q=pride&year=abcd", INVALID, "year"),
            new InvalidSearch("?q=pride&offset=-1", INVALID, "offset"),
            new InvalidSearch("?q=pride&offset=abc", INVALID, "offset"),
            new InvalidSearch("?q=pride&offset=99999999999999999999", INVALID, "offset"),
            new InvalidSearch("?q=pride&limit=0", INVALID, "limit"),
            new InvalidSearch("?q=pride&limit=101", INVALID, "limit"),
            new InvalidSearch("?q=pride&limit=-5", INVALID, "limit"),
            new InvalidSearch("?q=pride&limit=xyz", INVALID, "limit"),
            new InvalidSearch("?q=&author=...!&language=bad&year=bad&offset=-1&limit=0", MISSING, "q"),
            new InvalidSearch("?q=pride&author=...!&language=bad&year=bad&offset=-1&limit=0", INVALID, "author"),
            new InvalidSearch("?q=pride&author=Austen&language=bad&year=bad&offset=-1&limit=0", INVALID, "language"),
            new InvalidSearch("?q=pride&author=Austen&language=en&year=bad&offset=-1&limit=0", INVALID, "year"),
            new InvalidSearch("?q=pride&author=Austen&language=en&year=1998&offset=-1&limit=0", INVALID, "offset"));

    private record InvalidSearch(String query, ProblemCode code, String parameter) {
    }

    private final Api api;

    SearchParameterChecks(Api api) {
        this.api = api;
    }

    void check() throws IOException, InterruptedException {
        LOGGER.info("Checking parameter validation in strict order");
        for (InvalidSearch search : INVALID_SEARCHES) {
            assertProblem(api.frontEnd("/search" + search.query()), search.code(), search.parameter());
        }
    }
}
