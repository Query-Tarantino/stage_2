package org.ulpgc.tarantino.indexer.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.indexer.IndexerConfig;
import org.ulpgc.tarantino.indexer.IndexerFactory;
import org.ulpgc.tarantino.indexer.model.book.Book;
import org.ulpgc.tarantino.indexer.model.book.BookText;
import org.ulpgc.tarantino.indexer.model.book.HeaderParser;
import org.ulpgc.tarantino.indexer.model.terms.Tokenizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ulpgc.tarantino.crawler.conformance.ConformanceCases.cases;
import static org.ulpgc.tarantino.crawler.conformance.ConformanceCases.file;
import static org.ulpgc.tarantino.crawler.conformance.ConformanceCases.stopwords;
import static org.ulpgc.tarantino.crawler.conformance.ConformanceCases.textOrNull;

class IndexerConformanceTest {

    @TempDir
    Path root;

    @TestFactory
    Stream<DynamicTest> readsBooksAtTheirLayoutPaths() {
        return cases(file("datalake_paths.json")).stream().map(testCase -> DynamicTest.dynamicTest(name(testCase), () -> {
            String layout = testCase.get("layout").asText();
            int bookId = testCase.get("id").asInt();
            Path layoutRoot = root.resolve(layout + "-" + bookId);
            store(layoutRoot.resolve(testCase.get("header").asText()), "header of " + bookId);
            Path body = store(layoutRoot.resolve(testCase.get("body").asText()), "body of " + bookId);

            assertEquals(Optional.of(new BookText(bookId, "header of " + bookId, "body of " + bookId, body)),
                    IndexerFactory.datalakeReader(config(layoutRoot, layout)).bookText(bookId));
        }));
    }

    @TestFactory
    Stream<DynamicTest> extractsHeaderFields() {
        HeaderParser parser = new HeaderParser();
        return cases(file("header_fields.json")).stream().map(testCase -> DynamicTest.dynamicTest(testCase.get("name").asText(), () -> {
            Book book = parser.book(new BookText(1, testCase.get("header").asText(), "", Path.of("1.body.txt")));
            assertEquals(textOrNull(testCase.get("title")), book.title());
            assertEquals(textOrNull(testCase.get("author")), book.author());
            assertEquals(textOrNull(testCase.get("language")), book.language());
        }));
    }

    @TestFactory
    Stream<DynamicTest> countsTermsOfABody() {
        JsonNode terms = file("terms.json");
        Tokenizer tokenizer = new Tokenizer(stopwords(terms));
        return cases(terms).stream().map(testCase -> DynamicTest.dynamicTest(testCase.get("name").asText(), () -> {
            Map<String, Integer> counted = tokenizer.occurrences(1, testCase.get("text").asText()).frequencies();
            assertEquals(frequencies(testCase.get("terms")), new TreeMap<>(counted));
        }));
    }

    private static Path store(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private static IndexerConfig config(Path datalake, String layout) {
        return new IndexerConfig(datalake, layout, Path.of("datamarts"), "json", "sqlite", "mongodb://localhost:27017",
                Path.of("workload"));
    }

    private static String name(JsonNode testCase) {
        return testCase.get("layout").asText() + " " + testCase.get("id").asInt() + " at " + testCase.get("saved_at").asText();
    }

    private static Map<String, Integer> frequencies(JsonNode terms) {
        Map<String, Integer> frequencies = new TreeMap<>();
        terms.properties().forEach(term -> frequencies.put(term.getKey(), term.getValue().asInt()));
        return frequencies;
    }
}
