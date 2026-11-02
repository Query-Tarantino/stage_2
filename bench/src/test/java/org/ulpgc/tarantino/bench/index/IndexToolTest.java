package org.ulpgc.tarantino.bench.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndexToolTest {

    private static final String TRANSITION = " INFO org.ulpgc.tarantino.common.operations.TransitionLog - transition ";
    private static final Instant START = Instant.parse("2026-11-02T18:00:00Z");
    private static final int BOOKS = 10;

    @Test
    void writesEachTrialInFactorOrderAndSummarisesEachConfigurationOverItsRepetitions(@TempDir Path runDirectory) throws IOException {
        writeTrial(runDirectory, "batch-100-replicas-1-rep-1", 4, 5);
        writeTrial(runDirectory, "batch-10-replicas-2-rep-2", 3, 10);
        writeTrial(runDirectory, "batch-10-replicas-2-rep-1", 2, 10);

        assertEquals(IndexTool.DONE, IndexTool.run(List.of(runDirectory.toString())));

        assertEquals(List.of(
                        "batch,replicas,repetition,books,seconds,books_per_second",
                        "10,2,1,10,10.000,1.000",
                        "10,2,2,10,10.000,1.000",
                        "100,1,1,10,5.000,2.000"),
                Files.readAllLines(runDirectory.resolve(IndexTool.FILE_NAME)));
        assertEquals(List.of(
                        "metric,configuration,mean,std_dev,ci_95,p50,p95,p99,unit",
                        "index_throughput,\"batch=10,replicas=2\",1.00,0.00,0.00,1.00,1.00,1.00,books/s",
                        "index_throughput,\"batch=100,replicas=1\",2.00,0.00,0.00,2.00,2.00,2.00,books/s"),
                Files.readAllLines(runDirectory.resolve(SummaryStats.FILE_NAME)));
    }

    @Test
    void failsOnARunWithoutTrials(@TempDir Path runDirectory) {
        assertEquals(IndexTool.FAILED, IndexTool.run(List.of(runDirectory.toString())));
    }

    @Test
    void answersWrongUsageWithoutTheRunDirectory() {
        assertEquals(IndexTool.WRONG_USAGE, IndexTool.run(List.of()));
    }

    private static void writeTrial(Path runDirectory, String trial, int generation, int seconds) throws IOException {
        Path trialLogs = Files.createDirectories(runDirectory.resolve("logs").resolve(trial));
        Instant end = START.plusSeconds(seconds);
        Stream<String> published = IntStream.rangeClosed(1, BOOKS).mapToObj(book -> book(book, "INDEXING", "PUBLISHED", generation, end));
        Stream<String> claimed = IntStream.rangeClosed(1, BOOKS).mapToObj(book -> book(book, "DOWNLOADED", "INDEXING", generation, START));
        Files.write(trialLogs.resolve("indexer.log"), Stream.of(claimed, Stream.of(batch(generation, end)), published).flatMap(lines -> lines).toList());
    }

    private static String book(int bookId, String from, String to, int generation, Instant at) {
        return "indexer-1  | " + at + TRANSITION + "entity=book id=" + bookId + " from=" + from + " to=" + to
                + " batch_id=b generation=" + generation + " enqueued_at=- at=" + at + " replica=a";
    }

    private static String batch(int generation, Instant at) {
        return "indexer-1  | " + at + TRANSITION + "entity=batch id=b from=WRITING to=PUBLISHED batch_id=- generation=" + generation
                + " enqueued_at=- at=" + at + " replica=a";
    }
}
