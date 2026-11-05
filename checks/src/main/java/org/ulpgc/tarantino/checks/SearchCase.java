package org.ulpgc.tarantino.checks;

import java.util.List;

import static org.ulpgc.tarantino.checks.ApiNames.SEARCH;
import static org.ulpgc.tarantino.checks.SampleBooks.PRIDE_AND_PREJUDICE_YEAR;

/** A named search whose answer {@code checks/expected.json} records (SPEC §9), and the searches it records. */
record SearchCase(String name, String path) {

    private static final String PRIDE = SEARCH + "?q=pride";

    /** Words, each filter alone and together, pages, an offset past the last result and searches that find nothing. */
    static List<SearchCase> all(String germanTerm, String frenchTerm) {
        return List.of(
                new SearchCase("single_word", PRIDE),
                new SearchCase("multi_word", PRIDE + "%20prejudice"),
                new SearchCase("filter_author", PRIDE + "&author=Austen"),
                new SearchCase("filter_language", PRIDE + "&language=en"),
                new SearchCase("filter_language_de", SEARCH + "?q=" + germanTerm + "&language=de"),
                new SearchCase("filter_language_fr", SEARCH + "?q=" + frenchTerm + "&language=fr"),
                new SearchCase("filter_language_zz", PRIDE + "&language=zz"),
                new SearchCase("filter_year", PRIDE + "&year=" + PRIDE_AND_PREJUDICE_YEAR),
                new SearchCase("combined_filter", PRIDE + "&author=Austen&language=en&year=" + PRIDE_AND_PREJUDICE_YEAR),
                new SearchCase("pagination_page0", PRIDE + "&offset=0&limit=1"),
                new SearchCase("pagination_page1", PRIDE + "&offset=1&limit=1"),
                new SearchCase("pagination_large_offset", PRIDE + "&offset=" + SearchPageChecks.LARGE_OFFSET),
                new SearchCase("stopwords_only", SEARCH + "?q=the%20and"),
                new SearchCase("empty_results", SEARCH + "?q=pyromaniac%20liliput"));
    }
}
