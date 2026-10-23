package org.ulpgc.tarantino.query.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.ulpgc.tarantino.query.commands.SearchCommand;
import org.ulpgc.tarantino.query.model.BookMetadata;
import org.ulpgc.tarantino.query.model.QueryTerms;
import org.ulpgc.tarantino.query.ports.MetadataReader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ulpgc.tarantino.crawler.conformance.ConformanceCases.cases;
import static org.ulpgc.tarantino.crawler.conformance.ConformanceCases.file;
import static org.ulpgc.tarantino.crawler.conformance.ConformanceCases.stopwords;

class QueryConformanceTest {

    private static final MetadataReader EVERY_BOOK = new MetadataReader() {
        @Override
        public Optional<BookMetadata> book(int bookId) {
            return Optional.of(new BookMetadata(bookId, null, null, null, Path.of(bookId + ".body.txt")));
        }

        @Override
        public List<BookMetadata> booksBy(String author) {
            return List.of();
        }
    };

    @TestFactory
    Stream<DynamicTest> turnsQueriesIntoTerms() {
        JsonNode queryTerms = file("query_terms.json");
        Set<String> stopwords = stopwords(queryTerms);
        return cases(queryTerms).stream().map(testCase -> DynamicTest.dynamicTest(testCase.get("name").asText(), () -> {
            Set<String> terms = QueryTerms.of(testCase.get("query").asText(), stopwords);
            assertEquals(texts(testCase.get("terms")), List.copyOf(terms));
        }));
    }

    @TestFactory
    Stream<DynamicTest> findsTheBooksContainingEveryTerm() {
        JsonNode search = file("search.json");
        Set<String> stopwords = stopwords(search);
        Map<String, Set<Integer>> postings = postings(search.get("books"), stopwords);
        SearchCommand command = new SearchCommand(term -> postings.getOrDefault(term, Set.of()), EVERY_BOOK, stopwords);
        return cases(search).stream().map(testCase -> DynamicTest.dynamicTest(testCase.get("query").asText(), () -> {
            List<BookMetadata> found = command.execute(testCase.get("query").asText()).books();
            assertEquals(ids(testCase.get("ids")), found.stream().map(BookMetadata::bookId).toList());
        }));
    }

    private static Map<String, Set<Integer>> postings(JsonNode books, Set<String> stopwords) {
        Map<String, Set<Integer>> postings = new HashMap<>();
        books.properties().forEach(book -> QueryTerms.of(book.getValue().asText(), stopwords)
                .forEach(term -> postings.computeIfAbsent(term, key -> new TreeSet<>()).add(Integer.valueOf(book.getKey()))));
        return postings;
    }

    private static List<String> texts(JsonNode array) {
        List<String> texts = new ArrayList<>();
        array.forEach(text -> texts.add(text.asText()));
        return texts;
    }

    private static List<Integer> ids(JsonNode array) {
        List<Integer> ids = new ArrayList<>();
        array.forEach(id -> ids.add(id.asInt()));
        return ids;
    }
}
