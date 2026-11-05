package org.ulpgc.tarantino.checks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.model.ProblemCode;

import java.io.IOException;
import java.util.List;

import static org.ulpgc.tarantino.checks.ApiNames.AUTHOR;
import static org.ulpgc.tarantino.checks.ApiNames.LANGUAGE;
import static org.ulpgc.tarantino.checks.ApiNames.LIMIT;
import static org.ulpgc.tarantino.checks.ApiNames.OFFSET;
import static org.ulpgc.tarantino.checks.ApiNames.SEARCH;
import static org.ulpgc.tarantino.checks.ApiNames.YEAR;

/** The validation of the {@code GET /search} parameters in the order of SPEC §6.3, where the first invalid one wins. */
final class SearchValidationChecks {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchValidationChecks.class);
    private static final String Q = "q";
    private static final String PRIDE = "?q=pride&";

    private final ProblemChecks problems;
    private final String searchUrl;

    SearchValidationChecks(ProblemChecks problems, String frontEndUrl) {
        this.problems = problems;
        this.searchUrl = frontEndUrl + SEARCH;
    }

    void check() throws IOException, InterruptedException {
        LOGGER.info("Checking parameter validation in strict order");
        for (String missing : List.of("", "?q=", "?q=%20")) {
            problems.get(searchUrl + missing, ProblemCode.MISSING_PARAMETER, Q);
        }
        invalid(AUTHOR, "...!");
        invalid(LANGUAGE, "eng", "E", "12");
        invalid(YEAR, "98", "19980", "abcd");
        invalid(OFFSET, "-1", "abc", "99999999999999999999");
        invalid(LIMIT, "0", "101", "-5", "xyz");
        checkFirstInvalidParameterWins();
    }

    private void invalid(String parameter, String... values) throws IOException, InterruptedException {
        for (String value : values) {
            problems.get(searchUrl + PRIDE + parameter + "=" + value, ProblemCode.INVALID_PARAMETER, parameter);
        }
    }

    private void checkFirstInvalidParameterWins() throws IOException, InterruptedException {
        problems.get(searchUrl + "?q=&author=...!&language=bad&year=bad&offset=-1&limit=0", ProblemCode.MISSING_PARAMETER, Q);
        problems.get(searchUrl + "?q=pride&author=...!&language=bad&year=bad&offset=-1&limit=0", ProblemCode.INVALID_PARAMETER, AUTHOR);
        problems.get(searchUrl + "?q=pride&author=Austen&language=bad&year=bad&offset=-1&limit=0", ProblemCode.INVALID_PARAMETER, LANGUAGE);
        problems.get(searchUrl + "?q=pride&author=Austen&language=en&year=bad&offset=-1&limit=0", ProblemCode.INVALID_PARAMETER, YEAR);
        problems.get(searchUrl + "?q=pride&author=Austen&language=en&year=1998&offset=-1&limit=0", ProblemCode.INVALID_PARAMETER, OFFSET);
    }
}
