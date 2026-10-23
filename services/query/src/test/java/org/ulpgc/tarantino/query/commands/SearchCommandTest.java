package org.ulpgc.tarantino.query.commands;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.query.model.SearchQuery;
import org.ulpgc.tarantino.query.model.SearchResponse;
import org.ulpgc.tarantino.query.model.SearchResult;
import org.ulpgc.tarantino.query.model.Snapshot;
import org.ulpgc.tarantino.query.model.TermPostings;
import org.ulpgc.tarantino.query.ports.MetadataReader;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchCommandTest {

    private static final Snapshot SNAPSHOT = new Snapshot(1, Set.of("a", "b"), Set.of(1342, 84, 345, 11));
    private static final List<TermPostings> POSTINGS = List.of(
            new TermPostings("pride", List.of(84, 345, 1342), List.of(6, 6, 55)),
            new TermPostings("prejudice", List.of(84, 345, 1342), List.of(2, 2, 11)),
            new TermPostings("rabbit", List.of(11), List.of(40)));
    private static final MetadataReader IN_REVERSE_ORDER = (generation, bookIds) -> bookIds.stream()
            .sorted(Comparator.reverseOrder())
            .map(bookId -> new SearchResult(bookId, "Title " + bookId, null, "en", 1998))
            .toList();

    @Test
    void ranksByTfIdfAndBreaksTiesByAscendingBookId() {
        assertEquals(List.of(1342, 84, 345), ids(search(POSTINGS).execute(SNAPSHOT, query("Pride, prejudice!", 0, 20))));
    }

    @Test
    void countsEveryMatchButAnswersOnlyThePageInItsOrder() {
        SearchResponse response = search(POSTINGS).execute(SNAPSHOT, query("pride prejudice", 1, 1));

        assertEquals(3, response.total());
        assertEquals(List.of(new SearchResult(84, "Title 84", null, "en", 1998)), response.results());
        assertEquals(new SearchResponse("pride prejudice", Map.of(), 3, 1, 1, response.results()), response);
    }

    @Test
    void findsNothingWhenATermHasNoBooks() {
        assertEquals(0, search(POSTINGS).execute(SNAPSHOT, query("pride unicorn", 0, 20)).total());
    }

    @Test
    void findsNothingWhenOnlyStopwordsAreLeft() {
        SearchResponse response = search(POSTINGS).execute(SNAPSHOT, query("the and", 0, 20));

        assertEquals(0, response.total());
        assertEquals(List.of(), response.results());
    }

    @Test
    void findsNothingInAnEmptySnapshot() {
        assertEquals(0, search(POSTINGS).execute(Snapshot.empty(1), query("pride", 0, 20)).total());
    }

    @Test
    void countsABookRepeatedWithinATermOnce() {
        List<TermPostings> repeated = List.of(
                new TermPostings("rabbit", List.of(11), List.of(40)),
                new TermPostings("rabbit", List.of(11, 84), List.of(40, 1)));

        assertEquals(List.of(11, 84), ids(search(repeated).execute(SNAPSHOT, query("rabbit", 0, 20))));
    }

    private static SearchCommand search(List<TermPostings> postings) {
        return new SearchCommand((generation, terms, batchIds) -> postings.stream().filter(posting -> terms.contains(posting.term())).toList(),
                IN_REVERSE_ORDER, new Tokenizer(Set.of("the", "and")));
    }

    private static SearchQuery query(String q, int offset, int limit) {
        return new SearchQuery(q, offset, limit);
    }

    private static List<Integer> ids(SearchResponse response) {
        return response.results().stream().map(SearchResult::bookId).toList();
    }
}
