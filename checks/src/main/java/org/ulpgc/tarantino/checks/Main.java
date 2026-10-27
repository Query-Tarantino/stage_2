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
            checker.checkAll(sampleIds);
            LOGGER.info("All checks completed successfully");
        } catch (Exception e) {
            LOGGER.error("Checks failed: {}", e.getMessage(), e);
            System.exit(1);
        }
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
