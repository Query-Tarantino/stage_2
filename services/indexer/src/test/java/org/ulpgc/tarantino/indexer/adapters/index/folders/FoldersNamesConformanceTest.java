package org.ulpgc.tarantino.indexer.adapters.index.folders;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ulpgc.tarantino.crawler.conformance.ConformanceCases.cases;
import static org.ulpgc.tarantino.crawler.conformance.ConformanceCases.file;

class FoldersNamesConformanceTest {

    @TestFactory
    Stream<DynamicTest> namesTermFiles() {
        Path root = Path.of("index");
        return cases(file("folders_names.json")).stream().map(testCase -> DynamicTest.dynamicTest(testCase.get("name").asText(), () ->
                assertEquals(root.resolve(testCase.get("path").asText()), TermFiles.file(root, testCase.get("term").asText()))));
    }
}
