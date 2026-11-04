package org.ulpgc.tarantino.checks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * {@code java -jar checks/target/checks-1.0-SNAPSHOT.jar [--generate-expected]}: runs the functional API checks
 * (SPEC §9) against the stack at {@code TARANTINO_FRONTEND_URL} and {@code TARANTINO_OPERATIONAL_URL}, or writes
 * {@code checks/expected.json} again.
 */
public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final String OPENAPI = "api/openapi.yaml";
    private static final String SAMPLE_IDS = "workload/sample_ids.txt";
    private static final String EXPECTED = "checks/expected.json";
    private static final String GENERATE_EXPECTED = "--generate-expected";

    public static void main(String[] args) {
        try {
            run(List.of(args));
        } catch (Exception | AssertionError failure) {
            LOGGER.error("Checks failed: {}", failure.getMessage(), failure);
            System.exit(1);
        }
    }

    private static void run(List<String> args) throws IOException, InterruptedException {
        List<Integer> sampleIds = sampleIds(repositoryFile(SAMPLE_IDS));
        Path expectedPath = RepositoryFiles.find(EXPECTED).orElse(Path.of(EXPECTED));
        ApiChecker checker = new ApiChecker(new CheckedClient(OpenApiValidator.load(repositoryFile(OPENAPI))),
                System.getenv().getOrDefault("TARANTINO_FRONTEND_URL", "http://localhost:8080"),
                System.getenv().getOrDefault("TARANTINO_OPERATIONAL_URL", "http://localhost:8081"));
        if (args.contains(GENERATE_EXPECTED)) {
            checker.generateExpected(sampleIds, expectedPath);
            LOGGER.info("Expected answers successfully generated at {}", expectedPath);
            return;
        }
        checker.checkAll(sampleIds, expectedPath);
        LOGGER.info("All checks completed successfully");
    }

    private static List<Integer> sampleIds(Path sampleIdsFile) throws IOException {
        return Files.readAllLines(sampleIdsFile).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(Integer::parseInt)
                .toList();
    }

    private static Path repositoryFile(String relativePath) {
        return RepositoryFiles.find(relativePath).orElseThrow(() -> new IllegalStateException("Could not find file: " + relativePath));
    }
}
