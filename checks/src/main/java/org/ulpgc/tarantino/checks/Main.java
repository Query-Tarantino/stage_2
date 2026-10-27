package org.ulpgc.tarantino.checks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            Path openApiPath = findFile("api/openapi.yaml");
            Path sampleIdsPath = findFile("workload/sample_ids.txt");
            List<Integer> sampleIds = Files.readAllLines(sampleIdsPath).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .map(Integer::parseInt)
                    .toList();
            String frontEndUrl = System.getenv().getOrDefault("TARANTINO_FRONTEND_URL", "http://localhost:8080");
            String operationalUrl = System.getenv().getOrDefault("TARANTINO_OPERATIONAL_URL", "http://localhost:8081");

            OpenApiValidator validator = OpenApiValidator.load(openApiPath);
            CheckedClient client = new CheckedClient(validator);
            ApiChecker checker = new ApiChecker(client, frontEndUrl, operationalUrl);

            Path expectedPath = resolveExpectedPath();
            boolean generateExpected = List.of(args).contains("--generate-expected");
            if (generateExpected) {
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

    private static Path resolveExpectedPath() {
        List<Path> candidates = List.of(
                Paths.get("checks/expected.json"),
                Paths.get("expected.json"),
                Paths.get("../checks/expected.json")
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
