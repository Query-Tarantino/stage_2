package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.terms.Tokenizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The executable checks of the HTTP API (SPEC §6) against the running stack, over the 20 books of
 * {@code workload/sample_ids.txt} and {@code checks/expected.json}: one class per group of endpoints, run in the order
 * each one needs.
 */
public class ApiChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiChecker.class);

    private final HealthChecks health;
    private final EntryPortChecks entryPorts;
    private final OperationalChecks operational;
    private final SampleIngestion ingestion;
    private final SnapshotRefreshWait snapshots;
    private final BookChecks books;
    private final SearchValidationChecks searchValidation;
    private final SearchChecks searches;
    private final SearchPageChecks pages;
    private final ExpectedAnswers expectedAnswers;
    private final ExpectedAnswersWriter expectedAnswersWriter;
    private final InternalRouteChecks internalRoutes;
    private final boolean orchestrator;

    /** {@code orchestrator} adds the checks of the internal routes, which only that mode has (SPEC §11.4). */
    public ApiChecker(CheckedClient client, String frontEndUrl, String operationalUrl, Tokenizer tokenizer, boolean orchestrator) {
        String frontEndPort = withoutTrailingSlash(frontEndUrl);
        String operationalPort = withoutTrailingSlash(operationalUrl);
        ProblemChecks problems = new ProblemChecks(client);
        SearchAnswers answers = new SearchAnswers(client, frontEndPort);
        SearchTerms terms = new SearchTerms(tokenizer);
        this.health = new HealthChecks(client);
        this.entryPorts = new EntryPortChecks(client, frontEndPort, operationalPort);
        this.operational = new OperationalChecks(client, problems, operationalPort);
        this.ingestion = new SampleIngestion(client, operationalPort);
        this.snapshots = new SnapshotRefreshWait(client, frontEndPort);
        this.books = new BookChecks(client, problems, frontEndPort);
        this.searchValidation = new SearchValidationChecks(problems, frontEndPort);
        this.searches = new SearchChecks(client, answers, terms, frontEndPort);
        this.pages = new SearchPageChecks(answers);
        this.expectedAnswers = new ExpectedAnswers(client, frontEndPort);
        this.expectedAnswersWriter = new ExpectedAnswersWriter(client, terms, frontEndPort);
        this.internalRoutes = new InternalRouteChecks(client);
        this.orchestrator = orchestrator;
    }

    public void checkAll(List<Integer> sampleIds, Path expectedPath) throws IOException, InterruptedException {
        if (!Files.exists(expectedPath)) {
            throw new AssertionError("Expected answers file not found at " + expectedPath);
        }
        LOGGER.info("Starting API conformance checks");
        health.check();
        entryPorts.check();
        operational.checkStatusPages();
        publishAndWaitUntilServed(sampleIds);
        operational.checkBookStatus();
        operational.checkIdempotency(sampleIds);
        List<JsonNode> sampleBooks = books.check(sampleIds);
        searches.checkFilters();
        searchValidation.check();
        searches.check(sampleBooks);
        pages.check();
        expectedAnswers.check(expectedPath);
        if (orchestrator) {
            internalRoutes.check(sampleIds.getFirst());
        }
        LOGGER.info("All API conformance checks passed successfully");
    }

    public void generateExpected(List<Integer> sampleIds, Path expectedPath) throws IOException, InterruptedException {
        publishAndWaitUntilServed(sampleIds);
        expectedAnswersWriter.write(sampleIds, expectedPath);
    }

    private void publishAndWaitUntilServed(List<Integer> sampleIds) throws IOException, InterruptedException {
        ingestion.publish(sampleIds);
        snapshots.waitUntilServed(sampleIds);
    }

    private static String withoutTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
