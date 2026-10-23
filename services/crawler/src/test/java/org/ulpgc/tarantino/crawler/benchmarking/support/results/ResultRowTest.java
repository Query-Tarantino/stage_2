package org.ulpgc.tarantino.crawler.benchmarking.support.results;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultRowTest {

    @Test
    void writesTheSpecCsvLineWithThreeDecimals() {
        assertEquals("java,time,write_throughput,1000,812.400,35.250,books/s",
                new ResultRow("time", "write_throughput", 1000, 812.4, 35.25, "books/s").csvLine());
    }

    @Test
    void writesAnUnknownErrorAsAnEmptyField() {
        assertEquals("java,json,query_time,100,2.958,,µs/query",
                ResultRow.sample("json", "query_time", 100, 2.958, "µs/query").csvLine());
    }

    @Test
    void exactRowsHaveNoError() {
        assertEquals(0, ResultRow.exact("book", "file_count", 100, 200, "files").error());
    }

    @Test
    void readsBackTheLinesItWrites() {
        ResultRow row = new ResultRow("mongo", "disk_usage", 500, 34_700_000, 0, "bytes");

        assertEquals(row, ResultRow.parse(row.csvLine()));
        assertTrue(Double.isNaN(ResultRow.parse("java,json,query_time,100,2.958,,µs/query").error()));
    }
}
