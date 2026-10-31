package org.ulpgc.tarantino.bench.ingest;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.bench.coordination.TransitionLine;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IngestThroughputTest {

    private static final Instant START = Instant.parse("2026-10-31T13:54:00Z");

    @Test
    void measuresTheWholeWindowFromTheFirstClaimAndTheSteadyOneBetweenTheTenthAndNinetiethPercentDownloads() {
        List<TransitionLine> lines = Stream.concat(Stream.of(claim("1", 0)),
                IntStream.rangeClosed(1, 10).mapToObj(book -> downloaded(String.valueOf(book), book))).toList();

        IngestThroughput throughput = IngestThroughput.of(lines).orElseThrow();

        assertEquals(new IngestThroughput.Window(10, 10.0), throughput.whole());
        assertEquals(new IngestThroughput.Window(8, 8.0), throughput.steady());
        assertEquals(1.0, throughput.whole().booksPerSecond());
    }

    @Test
    void countsABookDownloadedTwiceOnceAtItsFirstDownload() {
        List<TransitionLine> lines = Stream.concat(Stream.of(claim("1", 0), downloaded("1", 1), downloaded("1", 30)),
                IntStream.rangeClosed(2, 10).mapToObj(book -> downloaded(String.valueOf(book), book))).toList();

        assertEquals(new IngestThroughput.Window(10, 10.0), IngestThroughput.of(lines).orElseThrow().whole());
    }

    @Test
    void hasNoThroughputWithoutAClaimOrWithoutTimeBetweenDownloads() {
        assertEquals(Optional.empty(), IngestThroughput.of(List.of(downloaded("1", 1), downloaded("2", 2))));
        assertEquals(Optional.empty(), IngestThroughput.of(List.of(claim("1", 0), downloaded("1", 1))));
    }

    private static TransitionLine claim(String bookId, int second) {
        return new TransitionLine(TransitionLine.BOOK, bookId, "QUEUED", "DOWNLOADING", null, START, START.plusSeconds(second));
    }

    private static TransitionLine downloaded(String bookId, int second) {
        return new TransitionLine(TransitionLine.BOOK, bookId, "DOWNLOADING", "DOWNLOADED", null, START, START.plusSeconds(second));
    }
}
