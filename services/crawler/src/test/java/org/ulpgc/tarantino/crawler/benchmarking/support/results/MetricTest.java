package org.ulpgc.tarantino.crawler.benchmarking.support.results;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricTest {

    private static final double PRECISION = 1e-9;

    @Test
    void keepsMeasuredScoresAndErrorsAsTheyAre() {
        ResultRow row = Metric.asMeasured("lookup_time", "µs/op").row("book", 100, 0.43, 0.08);

        assertEquals(new ResultRow("book", "lookup_time", 100, 0.43, 0.08, "µs/op"), row);
    }

    @Test
    void turnsTheTimeToWriteNBooksIntoThroughputKeepingTheRelativeError() {
        ResultRow row = Metric.booksPerSecond("write_throughput").row("batch", 100, 250, 25);

        assertEquals(400, row.value(), PRECISION);
        assertEquals(40, row.error(), PRECISION);
        assertEquals("books/s", row.unit());
    }

    @Test
    void dividesTheTimeOfARunByItsBooks() {
        ResultRow row = Metric.perBook("incremental_update_time", 10).row("json", 2000, 2556, 120);

        assertEquals(255.6, row.value(), PRECISION);
        assertEquals(12, row.error(), PRECISION);
        assertEquals("ms/book", row.unit());
    }

    @Test
    void keepsAnUnknownErrorUnknown() {
        assertEquals(Double.NaN, Metric.asMeasured("query_time", "µs/query").row("json", 100, 2.9, Double.NaN).error());
    }
}
