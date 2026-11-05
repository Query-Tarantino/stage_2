package org.ulpgc.tarantino.checks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** Runs the API checks (SPEC §6) against the stack or, with {@code --generate-expected}, writes {@code checks/expected.json}. */
public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            ApiChecker checker = checker();
            List<Integer> sampleIds = sampleIds(findFile("workload/sample_ids.txt"));
            Path expectedPath = resolveExpectedPath();
            if (List.of(args).contains("--generate-expected")) {
                checker.generateExpected(sampleIds, expectedPath);
                LOGGER.info("Expected answers successfully generated at {}", expectedPath);
                return;
            }
            checker.checkAll(sampleIds, expectedPath);
            LOGGER.info("All checks completed successfully");
        } catch (Exception exception) {
            LOGGER.error("Checks failed: {}", exception.getMessage(), exception);
            System.exit(1);
        }
    }

    private static ApiChecker checker() throws IOException {
        String frontEndUrl = System.getenv().getOrDefault("TARANTINO_FRONTEND_URL", "http://localhost:8080");
        String operationalUrl = System.getenv().getOrDefault("TARANTINO_OPERATIONAL_URL", "http://localhost:8081");
        OpenApiValidator validator = OpenApiValidator.load(findFile("api/openapi.yaml"));
        return new ApiChecker(new CheckedClient(validator), frontEndUrl, operationalUrl);
    }

    private static List<Integer> sampleIds(Path sampleIdsPath) throws IOException {
        return Files.readAllLines(sampleIdsPath).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(Integer::parseInt)
                .toList();
    }

    private static Path resolveExpectedPath() {
        List<Path> candidates = List.of(
                Paths.get("expected.json"),
                Paths.get("checks/expected.json"),
                Paths.get("check/expected.json"),
                Paths.get("../checks/expected.json"),
                Paths.get("../check/expected.json")
        );
        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElse(Paths.get("checks/expected.json"));
    }

    private static Path findFile(String relativePath) {
        List<Path> candidates = List.of(
                Paths.get(relativePath),
                Paths.get("../" + relativePath),
                Paths.get("../../" + relativePath)
        );
        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find file: " + relativePath));
    }
}
