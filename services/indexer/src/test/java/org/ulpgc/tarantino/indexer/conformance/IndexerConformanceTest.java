package org.ulpgc.tarantino.indexer.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.indexer.IndexerFactory;
import org.ulpgc.tarantino.indexer.model.book.Book;
import org.ulpgc.tarantino.indexer.model.book.BookText;
import org.ulpgc.tarantino.indexer.model.book.HeaderParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ulpgc.tarantino.common.conformance.ConformanceCases.cases;
import static org.ulpgc.tarantino.common.conformance.ConformanceCases.file;
import static org.ulpgc.tarantino.common.conformance.ConformanceCases.textOrNull;

class IndexerConformanceTest {

    @TempDir
    Path root;

    @TestFactory
    Stream<DynamicTest> readsBooksAtTheirBatchPaths() {
        return cases(file("datalake_paths.json")).stream().map(testCase -> DynamicTest.dynamicTest(name(testCase), () -> {
            int bookId = testCase.get("id").asInt();
            Path bookRoot = root.resolve(String.valueOf(bookId));
            store(bookRoot.resolve(testCase.get("header").asText()), "header of " + bookId);
            Path body = store(bookRoot.resolve(testCase.get("body").asText()), "body of " + bookId);

            assertEquals(Optional.of(new BookText(bookId, "header of " + bookId, "body of " + bookId, body)),
                    IndexerFactory.datalakeReader(config(bookRoot)).bookText(bookId));
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

    private static Path store(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private static TarantinoConfig config(Path datalake) {
        return TarantinoConfig.from(Map.of("TARANTINO_DATALAKE", datalake.toString()), TarantinoService.INDEXER);
    }

    private static String name(JsonNode testCase) {
        return "book " + testCase.get("id").asInt();
    }
}
