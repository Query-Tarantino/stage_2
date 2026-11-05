package org.ulpgc.tarantino.checks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.config.Coordination;
import org.ulpgc.tarantino.common.terms.FileStopwordsLoader;
import org.ulpgc.tarantino.common.terms.Tokenizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Runs the API checks (SPEC §6) against the stack or, with {@code --generate-expected}, writes {@code checks/expected.json}.
 * With {@code TARANTINO_COORDINATION=orchestrator} it also checks the internal routes of that mode (SPEC §11.4).
 */
public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final String FRONT_END_URL = "TARANTINO_FRONTEND_URL";
    private static final String DEFAULT_FRONT_END_URL = "http://localhost:8080";
    private static final String OPERATIONAL_URL = "TARANTINO_OPERATIONAL_URL";
    private static final String DEFAULT_OPERATIONAL_URL = "http://localhost:8081";
    private static final String COORDINATION = "TARANTINO_COORDINATION";
    private static final String GENERATE_EXPECTED = "--generate-expected";
    private static final String OPENAPI = "api/openapi.yaml";
    private static final String STOPWORDS = "workload/stopwords.txt";
    private static final String SAMPLE_IDS = "workload/sample_ids.txt";
    private static final String EXPECTED = "checks/expected.json";
    private static final int FAILED = 1;

    public static void main(String[] args) {
        try {
            ApiChecker checker = checker();
            List<Integer> sampleIds = sampleIds(RepositoryFiles.require(SAMPLE_IDS));
            Path expectedPath = RepositoryFiles.find(EXPECTED).orElse(Path.of(EXPECTED));
            if (List.of(args).contains(GENERATE_EXPECTED)) {
                checker.generateExpected(sampleIds, expectedPath);
                LOGGER.info("Expected answers successfully generated at {}", expectedPath);
                return;
            }
            checker.checkAll(sampleIds, expectedPath);
            LOGGER.info("All checks completed successfully");
        } catch (Exception | AssertionError failure) {
            LOGGER.error("Checks failed: {}", failure.getMessage(), failure);
            System.exit(FAILED);
        }
    }

    private static ApiChecker checker() throws IOException {
        String frontEndUrl = System.getenv().getOrDefault(FRONT_END_URL, DEFAULT_FRONT_END_URL);
        String operationalUrl = System.getenv().getOrDefault(OPERATIONAL_URL, DEFAULT_OPERATIONAL_URL);
        OpenApiValidator validator = OpenApiValidator.load(RepositoryFiles.require(OPENAPI));
        Tokenizer tokenizer = new Tokenizer(new FileStopwordsLoader(RepositoryFiles.require(STOPWORDS)).stopwords());
        boolean orchestrator = Coordination.ORCHESTRATOR.variableValue().equals(System.getenv(COORDINATION));
        return new ApiChecker(new CheckedClient(validator), frontEndUrl, operationalUrl, tokenizer, orchestrator);
    }

    private static List<Integer> sampleIds(Path sampleIdsPath) throws IOException {
        return Files.readAllLines(sampleIdsPath).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(Integer::parseInt)
                .toList();
    }
}
