package org.ulpgc.tarantino.crawler.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The language-neutral cases of TARANTINO_WORKLOAD/conformance (SPEC §13), which every implementation must pass.
 * Without the variable, workload/conformance is looked for from the working directory upwards, so the tests run
 * from the project root, from a module directory and from an IDE alike.
 */
public final class ConformanceCases {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path DIRECTORY = directory();

    private ConformanceCases() {
    }

    private static Path directory() {
        String workload = System.getenv("TARANTINO_WORKLOAD");
        if (workload != null && !workload.isBlank()) {
            return Path.of(workload, "conformance");
        }
        for (Path directory = Path.of("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
            Path candidate = directory.resolve("workload").resolve("conformance");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("workload/conformance not found from " + Path.of("").toAbsolutePath());
    }

    public static JsonNode file(String name) {
        try {
            return MAPPER.readTree(DIRECTORY.resolve(name).toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<JsonNode> cases(JsonNode file) {
        List<JsonNode> cases = new ArrayList<>();
        file.get("cases").forEach(cases::add);
        return cases;
    }

    public static Set<String> stopwords(JsonNode file) {
        Set<String> stopwords = new TreeSet<>();
        file.get("stopwords").forEach(stopword -> stopwords.add(stopword.asText()));
        return stopwords;
    }

    public static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
