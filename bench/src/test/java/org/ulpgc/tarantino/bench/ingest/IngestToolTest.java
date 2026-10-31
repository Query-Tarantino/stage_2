package org.ulpgc.tarantino.bench.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.bench.stats.SummaryStats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IngestToolTest {

    private static final String TRANSITION = " INFO org.ulpgc.tarantino.common.operations.TransitionLog - transition ";
    private static final Instant START = Instant.parse("2026-10-31T13:54:00Z");
    private static final int BOOKS = 10;

    @TempDir
    Path runDirectory;

    @Test
    void writesEachTrialInFactorOrderAndSummarisesEachConfigurationOverItsRepetitions() throws IOException {
        writeTrial("replicas-1-workers-16-rep-1", Duration.ofMillis(500));
        writeTrial("replicas-1-workers-4-rep-2", Duration.ofSeconds(1));
        writeTrial("replicas-1-workers-4-rep-1", Duration.ofSeconds(1));

        assertEquals(IngestTool.DONE, IngestTool.run(List.of(runDirectory.toString())));

        assertEquals(List.of(
                        "replicas,workers,repetition,books,seconds,books_per_second,steady_books,steady_seconds,steady_books_per_second",
                        "1,4,1,10,10.000,1.000,8,8.000,1.000",
                        "1,4,2,10,10.000,1.000,8,8.000,1.000",
                        "1,16,1,10,5.000,2.000,8,4.000,2.000"),
                Files.readAllLines(runDirectory.resolve(IngestTool.FILE_NAME)));
        assertEquals(List.of(
                        "metric,configuration,mean,std_dev,ci_95,p50,p95,p99,unit",
                        "ingest_throughput,\"replicas=1,workers=4\",1.00,0.00,0.00,1,1,1,books/s",
                        "ingest_throughput_steady,\"replicas=1,workers=4\",1.00,0.00,0.00,1,1,1,books/s",
                        "ingest_throughput,\"replicas=1,workers=16\",2.00,0.00,0.00,2,2,2,books/s",
                        "ingest_throughput_steady,\"replicas=1,workers=16\",2.00,0.00,0.00,2,2,2,books/s"),
                Files.readAllLines(runDirectory.resolve(SummaryStats.FILE_NAME)));
    }

    @Test
    void failsWhenTheTrialsDoNotShareTheirFactors() throws IOException {
        writeTrial("replicas-1-workers-8-rep-1", Duration.ofSeconds(1));
        writeTrial("replicas-2-rep-1", Duration.ofSeconds(1));

        assertEquals(IngestTool.FAILED, IngestTool.run(List.of(runDirectory.toString())));
    }

    @Test
    void failsOnARunWithoutTrials() {
        assertEquals(IngestTool.FAILED, IngestTool.run(List.of(runDirectory.toString())));
    }

    @Test
    void answersWrongUsageWithoutTheRunDirectory() {
        assertEquals(IngestTool.WRONG_USAGE, IngestTool.run(List.of()));
    }

    private void writeTrial(String trial, Duration betweenDownloads) throws IOException {
        Path trialLogs = Files.createDirectories(runDirectory.resolve("logs").resolve(trial));
        Stream<String> downloads = IntStream.rangeClosed(1, BOOKS)
                .mapToObj(book -> line(book, "DOWNLOADING", "DOWNLOADED", START.plus(betweenDownloads.multipliedBy(book))));
        Files.write(trialLogs.resolve("crawler.log"), Stream.concat(Stream.of(line(1, "QUEUED", "DOWNLOADING", START)), downloads).toList());
    }

    private static String line(int bookId, String from, String to, Instant at) {
        return "crawler-1  | " + at + TRANSITION + "entity=book id=" + bookId + " from=" + from + " to=" + to
                + " batch_id=- generation=- enqueued_at=- at=" + at + " replica=a";
    }
}
