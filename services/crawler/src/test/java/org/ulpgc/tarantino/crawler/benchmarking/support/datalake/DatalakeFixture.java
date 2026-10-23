package org.ulpgc.tarantino.crawler.benchmarking.support.datalake;

import org.ulpgc.tarantino.crawler.CrawlerConfig;
import org.ulpgc.tarantino.crawler.CrawlerFactory;
import org.ulpgc.tarantino.crawler.adapters.datalake.time.TimeBasedDatalakeAdapter;
import org.ulpgc.tarantino.crawler.benchmarking.support.dataset.BenchmarkDataset;
import org.ulpgc.tarantino.crawler.benchmarking.support.files.Directories;
import org.ulpgc.tarantino.crawler.benchmarking.support.files.Footprint;
import org.ulpgc.tarantino.crawler.benchmarking.support.results.ResultRow;
import org.ulpgc.tarantino.crawler.benchmarking.support.validation.Check;
import org.ulpgc.tarantino.crawler.commands.IngestBookCommand;
import org.ulpgc.tarantino.crawler.commands.IngestResult;
import org.ulpgc.tarantino.crawler.model.book.StoredPaths;
import org.ulpgc.tarantino.crawler.ports.DatalakeStorage;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class DatalakeFixture {

    public static final List<String> LAYOUTS = List.of("time", "book", "batch");
    public static final Instant CRAWL_START = Instant.parse("2025-09-25T00:00:00Z");

    private DatalakeFixture() {
    }

    public static DatalakeStorage datalake(String layout, Path root) {
        return datalake(layout, root, Clock.systemUTC());
    }

    public static DatalakeStorage datalake(String layout, Path root, Clock clock) {
        return "time".equals(layout) ? new TimeBasedDatalakeAdapter(root, clock) : CrawlerFactory.datalake(new CrawlerConfig(root, layout));
    }

    /** Ingests the books as the crawler does: skipping stored ones, then reading, splitting and saving (SPEC §9). */
    public static List<StoredPaths> ingest(DatalakeStorage datalake, List<Integer> ids, BenchmarkDataset dataset) {
        IngestBookCommand ingest = new IngestBookCommand(dataset::rawText, datalake);
        return ids.stream().map(id -> stored(ingest.execute(id))).toList();
    }

    public static List<StoredPaths> ingestAsCrawled(String layout, Path root, List<Integer> ids, BenchmarkDataset dataset, Instant start) {
        CrawlClock clock = new CrawlClock(start);
        IngestBookCommand ingest = new IngestBookCommand(dataset::rawText, datalake(layout, root, clock));
        List<StoredPaths> stored = new ArrayList<>(ids.size());
        for (int position = 0; position < ids.size(); position++) {
            clock.moveTo(position);
            stored.add(stored(ingest.execute(ids.get(position))));
        }
        return stored;
    }

    private static StoredPaths stored(IngestResult result) {
        Check.require(result.succeeded(), "book " + result.bookId() + " not ingested: " + result.failure());
        return result.paths();
    }

    public static List<ResultRow> footprint(String layout, int books, Path root) {
        Footprint footprint = Directories.footprint(root);
        return List.of(
                ResultRow.exact(layout, "file_count", books, footprint.files(), "files"),
                ResultRow.exact(layout, "directory_count", books, footprint.directories(), "dirs"),
                ResultRow.exact(layout, "disk_usage", books, footprint.bytes(), "bytes"),
                ResultRow.exact(layout, "disk_allocated", books, footprint.allocatedBytes(), "bytes"));
    }
}
