package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

import static org.ulpgc.tarantino.checks.ApiNames.LIMIT;
import static org.ulpgc.tarantino.checks.ApiNames.OFFSET;
import static org.ulpgc.tarantino.checks.ApiNames.RESULTS;
import static org.ulpgc.tarantino.checks.ApiNames.SEARCH;
import static org.ulpgc.tarantino.checks.ApiNames.TOTAL;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertEquals;
import static org.ulpgc.tarantino.checks.CheckAssertions.assertTrue;

/** The pages of {@code GET /search}, an offset past the last result and searches that find nothing (SPEC §6.3). */
final class SearchPageChecks {

    static final int LARGE_OFFSET = 1_000_000_000;

    private static final String PRIDE = SEARCH + "?q=pride";

    private final SearchAnswers answers;

    SearchPageChecks(SearchAnswers answers) {
        this.answers = answers;
    }

    void check() throws IOException, InterruptedException {
        JsonNode first = page(0);
        JsonNode second = page(1);
        assertEquals(first.get(TOTAL).asInt(), second.get(TOTAL).asInt(), "the total of the second page of " + PRIDE);
        if (first.get(TOTAL).asInt() >= 2) {
            assertTrue(!SearchResults.bookIds(first.get(RESULTS)).equals(SearchResults.bookIds(second.get(RESULTS))),
                    "pages 0 and 1 of " + PRIDE + " start with different books");
        }
        String pastTheLast = PRIDE + "&offset=" + LARGE_OFFSET;
        JsonNode answer = answers.answer(pastTheLast);
        assertEquals(LARGE_OFFSET, answer.get(OFFSET).asInt(), "the offset of " + pastTheLast);
        assertEquals(0, answer.get(RESULTS).size(), "the results of " + pastTheLast);
        answers.findsNothing(SEARCH + "?q=the%20and");
        answers.findsNothing(SEARCH + "?q=pyromaniac%20liliput");
    }

    private JsonNode page(int offset) throws IOException, InterruptedException {
        String path = PRIDE + "&offset=" + offset + "&limit=1";
        JsonNode answer = answers.answer(path);
        assertEquals(offset, answer.get(OFFSET).asInt(), "the offset of " + path);
        assertEquals(1, answer.get(LIMIT).asInt(), "the limit of " + path);
        return answer;
    }
}
