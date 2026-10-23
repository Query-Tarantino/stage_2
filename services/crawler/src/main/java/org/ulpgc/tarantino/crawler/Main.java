package org.ulpgc.tarantino.crawler;

import org.ulpgc.tarantino.crawler.commands.IngestBookCommand;
import org.ulpgc.tarantino.crawler.commands.IngestResult;

import java.util.Arrays;

public class Main {

    public static void main(String[] args) {
        CrawlerConfig config = CrawlerConfig.fromEnvironment();
        IngestBookCommand ingest = CrawlerFactory.ingestCommand(config);
        Arrays.stream(args).map(Integer::parseInt).map(ingest::execute).map(Main::line).forEach(System.out::println);
    }

    private static String line(IngestResult result) {
        return "[CRAWLER] " + result.bookId() + ": "
                + (result.succeeded() ? "stored in " + result.paths().body().getParent() : "skipped, " + result.failure());
    }
}
