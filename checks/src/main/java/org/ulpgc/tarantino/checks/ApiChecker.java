package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.terms.FileStopwordsLoader;
import org.ulpgc.tarantino.common.terms.Tokenizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

/** The executable checks of the HTTP API (SPEC §6) against the running stack, over the 20 books of {@code workload/sample_ids.txt} and {@code checks/expected.json}. */
public class ApiChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiChecker.class);

    private final CheckedClient client;
    private final String frontEndUrl;
    private final String operationalUrl;
    private final SearchTerms terms;
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

    public ApiChecker(CheckedClient client, String frontEndUrl, String operationalUrl) {
        this.client = client;
        this.frontEndUrl = trimSlash(frontEndUrl);
        this.operationalUrl = trimSlash(operationalUrl);
        Path stopwordsPath = findStopwordsPath();
        Set<String> stopwords = Files.exists(stopwordsPath)
                ? new FileStopwordsLoader(stopwordsPath).stopwords()
                : Set.of();
        this.terms = new SearchTerms(new Tokenizer(stopwords));
        ProblemChecks problems = new ProblemChecks(client);
        this.health = new HealthChecks(client);
        this.entryPorts = new EntryPortChecks(client, this.frontEndUrl, this.operationalUrl);
        this.operational = new OperationalChecks(client, problems, this.operationalUrl);
        this.ingestion = new SampleIngestion(client, this.operationalUrl);
        this.snapshots = new SnapshotRefreshWait(client, this.frontEndUrl);
        this.books = new BookChecks(client, problems, this.frontEndUrl);
        this.searchValidation = new SearchValidationChecks(problems, this.frontEndUrl);
        SearchAnswers answers = new SearchAnswers(client, this.frontEndUrl);
        this.searches = new SearchChecks(client, answers, terms, this.frontEndUrl);
        this.pages = new SearchPageChecks(answers);
        this.expectedAnswers = new ExpectedAnswers(client, this.frontEndUrl);
        this.expectedAnswersWriter = new ExpectedAnswersWriter(client, terms, this.frontEndUrl);
    }

    public void checkAll(List<Integer> sampleIds, Path expectedPath) throws IOException, InterruptedException {
        if (expectedPath == null || !Files.exists(expectedPath)) {
            throw new AssertionError("Expected answers file not found at " + expectedPath);
        }
        LOGGER.info("Starting API conformance checks");
        health.check();
        entryPorts.check();
        operational.checkStatusPages();
        ensurePublishedAndRefreshed(sampleIds);
        operational.checkBookStatus();
        operational.checkIdempotency(sampleIds);
        List<JsonNode> sampleBooks = books.check(sampleIds);
        searches.checkFilters();
        searchValidation.check();
        searches.check(sampleBooks);
        pages.check();
        expectedAnswers.check(expectedPath);
        LOGGER.info("All API conformance checks passed successfully");
    }

    public void ensurePublishedAndRefreshed(List<Integer> sampleIds) throws IOException, InterruptedException {
        ingestion.publish(sampleIds);
        snapshots.waitUntilServed(sampleIds);
    }

    private static Path findStopwordsPath() {
        List<Path> candidates = List.of(
                Paths.get("workload/stopwords.txt"),
                Paths.get("../workload/stopwords.txt"),
                Paths.get("../../workload/stopwords.txt")
        );
        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElse(Paths.get("workload/stopwords.txt"));
    }

    public void generateExpected(List<Integer> sampleIds, Path expectedPath) throws IOException, InterruptedException {
        ensurePublishedAndRefreshed(sampleIds);
        expectedAnswersWriter.write(sampleIds, expectedPath);
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
