package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The functional API checks (F4, SPEC §9) against the running stack through nginx: every response is validated against
 * {@code api/openapi.yaml}, and the answers over the sample books against {@code checks/expected.json}.
 */
public class ApiChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiChecker.class);

    private final HealthChecks health;
    private final NginxChecks nginx;
    private final OperationalChecks operational;
    private final PublicationWait publication;
    private final BookChecks books;
    private final SearchParameterChecks parameters;
    private final SearchChecks searches;
    private final ExpectedChecks expected;
    private final ExpectedGenerator generator;

    public ApiChecker(CheckedClient client, String frontEndUrl, String operationalUrl) {
        Api api = Api.of(client, frontEndUrl, operationalUrl);
        SearchTerms terms = SearchTerms.fromRepository();
        this.health = new HealthChecks(client);
        this.nginx = new NginxChecks(api);
        this.operational = new OperationalChecks(api);
        this.publication = new PublicationWait(api);
        this.books = new BookChecks(api);
        this.parameters = new SearchParameterChecks(api);
        this.searches = new SearchChecks(api, terms);
        this.expected = new ExpectedChecks(api);
        this.generator = new ExpectedGenerator(api, terms, publication);
    }

    public void checkAll(List<Integer> sampleIds, Path expectedPath) throws IOException, InterruptedException {
        if (expectedPath == null || !Files.exists(expectedPath)) {
            throw new AssertionError("Expected answers file not found at " + expectedPath);
        }
        LOGGER.info("Starting API conformance checks");
        health.check();
        nginx.check();
        operational.checkSummaries();
        publication.ensurePublished(sampleIds);
        operational.checkBookStatuses();
        operational.checkIdempotency(sampleIds);
        List<JsonNode> sampleBooks = books.check(sampleIds);
        searches.checkFilterSummary();
        parameters.check();
        searches.check(sampleBooks);
        expected.check(expectedPath);
        LOGGER.info("All API conformance checks passed successfully");
    }

    /** Writes {@code checks/expected.json} again, for when the corpus or the schema changes. */
    public void generateExpected(List<Integer> sampleIds, Path expectedPath) throws IOException, InterruptedException {
        generator.generate(sampleIds, expectedPath);
    }
}
