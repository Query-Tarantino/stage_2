package org.ulpgc.tarantino.bench.variants;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariantsToolTest {

    private static final Instant START = Instant.parse("2026-11-07T10:00:00Z");

    @TempDir
    Path run;

    @Test
    void comparesTheThroughputOfTwoModesAndKeepsTheSummaryRowsWithTheirMode() throws IOException {
        trial("mode-hazelcast-replicas-1-rep-1", 10, 2);
        trial("mode-hazelcast-replicas-1-rep-2", 10, 2);
        trial("mode-polling-replicas-1-rep-1", 10, 4);
        trial("mode-polling-replicas-1-rep-2", 10, 5);
        Files.write(run.resolve("summary.csv"), List.of("metric,configuration,mean,std_dev,ci_95,p50,p95,p99,unit",
                "download_queue_wait,\"mode=polling,replicas=1\",10.00,1.00,1.00,10.00,12.00,12.00,ms"));
        System.setProperty("java.awt.headless", "true");

        assertEquals(0, VariantsTool.run(List.of(run.toString())));

        List<String> pairs = Files.readAllLines(run.resolve(VariantsTool.PAIRS_FILE));
        assertEquals(ModePair.HEADER, pairs.getFirst());
        assertTrue(pairs.contains("pipeline_throughput,1,hazelcast,polling,5.000,2.250,2.750,3.177,false,books/s"), pairs.toString());
        List<String> variants = Files.readAllLines(run.resolve(VariantsTool.VARIANTS_FILE));
        assertTrue(variants.contains("polling,download_queue_wait,\"mode=polling,replicas=1\",10.00,1.00,1.00,10.00,12.00,12.00,ms"), variants.toString());
        assertTrue(variants.stream().anyMatch(row -> row.startsWith("hazelcast,pipeline_throughput,\"mode=hazelcast,replicas=1\",5.00")), variants.toString());
        assertTrue(Files.exists(run.resolve("charts/pipeline-throughput-pairs.png")));
    }

    private void trial(String name, int books, int seconds) throws IOException {
        Path logs = Files.createDirectories(run.resolve("logs").resolve(name));
        List<String> lines = new ArrayList<>();
        for (int book = 1; book <= books; book++) {
            lines.add(line(book, "QUEUED", "DOWNLOADING", START));
            lines.add(line(book, "INDEXING", "PUBLISHED", START.plusMillis(seconds * 1000L * book / books)));
        }
        Files.write(logs.resolve("crawler.log"), lines);
    }

    private static String line(int bookId, String from, String to, Instant at) {
        return "transition entity=book id=" + bookId + " from=" + from + " to=" + to + " batch_id=- generation=- enqueued_at=- at=" + at
                + " replica=r1";
    }
}
