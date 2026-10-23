package org.ulpgc.tarantino.crawler.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.crawler.CrawlerConfig;
import org.ulpgc.tarantino.crawler.CrawlerFactory;
import org.ulpgc.tarantino.crawler.adapters.datalake.time.TimeBasedDatalakeAdapter;
import org.ulpgc.tarantino.crawler.model.book.BookText;
import org.ulpgc.tarantino.crawler.model.book.GutenbergText;
import org.ulpgc.tarantino.crawler.model.book.StoredPaths;
import org.ulpgc.tarantino.crawler.model.failure.DownloadException;
import org.ulpgc.tarantino.crawler.ports.DatalakeStorage;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.ulpgc.tarantino.crawler.conformance.ConformanceCases.cases;
import static org.ulpgc.tarantino.crawler.conformance.ConformanceCases.file;

class CrawlerConformanceTest {

    @TempDir
    Path root;

    @TestFactory
    Stream<DynamicTest> splitsHeaderAndBody() {
        return cases(file("split.json")).stream().map(testCase -> DynamicTest.dynamicTest(testCase.get("name").asText(), () -> {
            String raw = testCase.get("raw").asText();
            if (testCase.has("failure")) {
                DownloadException failure = assertThrows(DownloadException.class, () -> GutenbergText.bookText(1, raw));
                assertEquals(testCase.get("failure").asText(), failure.reason().name());
            } else {
                BookText text = GutenbergText.bookText(1, raw);
                assertEquals(testCase.get("header").asText(), text.header());
                assertEquals(testCase.get("body").asText(), text.body());
            }
        }));
    }

    @TestFactory
    Stream<DynamicTest> storesBooksAtTheirLayoutPaths() {
        return cases(file("datalake_paths.json")).stream().map(testCase -> DynamicTest.dynamicTest(name(testCase), () -> {
            Path layoutRoot = root.resolve(testCase.get("layout").asText() + "-" + testCase.get("id").asInt());
            StoredPaths paths = datalake(testCase, layoutRoot).save(new BookText(testCase.get("id").asInt(), "header", "body"));
            assertEquals(testCase.get("header").asText(), relative(layoutRoot, paths.header()));
            assertEquals(testCase.get("body").asText(), relative(layoutRoot, paths.body()));
        }));
    }

    private static DatalakeStorage datalake(JsonNode testCase, Path root) {
        String layout = testCase.get("layout").asText();
        if (!"time".equals(layout)) {
            return CrawlerFactory.datalake(new CrawlerConfig(root, layout));
        }
        return new TimeBasedDatalakeAdapter(root, Clock.fixed(Instant.parse(testCase.get("saved_at").asText()), ZoneOffset.UTC));
    }

    private static String name(JsonNode testCase) {
        return testCase.get("layout").asText() + " " + testCase.get("id").asInt() + " at " + testCase.get("saved_at").asText();
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
