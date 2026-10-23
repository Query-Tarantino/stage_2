package org.ulpgc.tarantino.common.terms;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ulpgc.tarantino.common.conformance.ConformanceCases.cases;
import static org.ulpgc.tarantino.common.conformance.ConformanceCases.file;
import static org.ulpgc.tarantino.common.conformance.ConformanceCases.stopwords;

class TermsConformanceTest {

    @TestFactory
    Stream<DynamicTest> countsTermsOfABody() {
        JsonNode terms = file("terms.json");
        Tokenizer tokenizer = new Tokenizer(stopwords(terms));
        return cases(terms).stream().map(testCase -> DynamicTest.dynamicTest(testCase.get("name").asText(), () ->
                assertEquals(frequencies(testCase.get("terms")), new TreeMap<>(tokenizer.frequencies(testCase.get("text").asText())))));
    }

    @TestFactory
    Stream<DynamicTest> turnsQueriesIntoDistinctTerms() {
        JsonNode queryTerms = file("query_terms.json");
        Tokenizer tokenizer = new Tokenizer(stopwords(queryTerms));
        return cases(queryTerms).stream().map(testCase -> DynamicTest.dynamicTest(testCase.get("name").asText(), () ->
                assertEquals(texts(testCase.get("terms")), tokenizer.distinctTerms(testCase.get("query").asText()))));
    }

    private static Map<String, Integer> frequencies(JsonNode terms) {
        Map<String, Integer> frequencies = new TreeMap<>();
        terms.properties().forEach(term -> frequencies.put(term.getKey(), term.getValue().asInt()));
        return frequencies;
    }

    private static List<String> texts(JsonNode array) {
        List<String> texts = new ArrayList<>();
        array.forEach(text -> texts.add(text.asText()));
        return texts;
    }
}
