package org.ulpgc.tarantino.query.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.query.commands.SearchCommand;
import org.ulpgc.tarantino.query.model.SearchQuery;
import org.ulpgc.tarantino.query.model.SearchResult;
import org.ulpgc.tarantino.query.model.Snapshot;
import org.ulpgc.tarantino.query.model.TermPostings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ulpgc.tarantino.common.conformance.ConformanceCases.cases;
import static org.ulpgc.tarantino.common.conformance.ConformanceCases.file;
import static org.ulpgc.tarantino.common.conformance.ConformanceCases.stopwords;

class QueryConformanceTest {

    private static final String BATCH = "batch";

    @TestFactory
    Stream<DynamicTest> findsTheBooksContainingEveryTerm() {
        JsonNode search = file("search.json");
        Tokenizer tokenizer = new Tokenizer(stopwords(search));
        Map<Integer, Map<String, Integer>> frequencies = new TreeMap<>();
        search.get("books").properties().forEach(book -> frequencies.put(Integer.valueOf(book.getKey()), tokenizer.frequencies(book.getValue().asText())));
        List<TermPostings> postings = postings(frequencies);
        SearchCommand command = new SearchCommand(
                (generation, terms, batchIds) -> postings.stream().filter(posting -> terms.contains(posting.term())).toList(),
                (generation, bookIds) -> bookIds.stream().map(bookId -> new SearchResult(bookId, null, null, null, null)).toList(),
                tokenizer);
        Snapshot snapshot = new Snapshot(1, Set.of(BATCH), frequencies.keySet());
        return cases(search).stream().map(testCase -> DynamicTest.dynamicTest(testCase.get("query").asText(), () -> {
            List<SearchResult> found = command.execute(snapshot, new SearchQuery(testCase.get("query").asText(), 0, 20)).results();
            assertEquals(ids(testCase.get("ids")), found.stream().map(SearchResult::bookId).toList());
        }));
    }

    private static List<TermPostings> postings(Map<Integer, Map<String, Integer>> frequencies) {
        Map<String, Map<Integer, Integer>> booksByTerm = frequencies.entrySet().stream()
                .flatMap(book -> book.getValue().entrySet().stream().map(term -> Map.entry(term.getKey(), Map.entry(book.getKey(), term.getValue()))))
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.toMap(entry -> entry.getValue().getKey(),
                        entry -> entry.getValue().getValue(), Integer::sum, TreeMap::new)));
        return booksByTerm.entrySet().stream()
                .map(term -> new TermPostings(term.getKey(), List.copyOf(term.getValue().keySet()), List.copyOf(term.getValue().values())))
                .toList();
    }

    private static List<Integer> ids(JsonNode array) {
        List<Integer> ids = new ArrayList<>();
        array.forEach(id -> ids.add(id.asInt()));
        return ids;
    }
}
