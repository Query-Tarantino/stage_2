package org.ulpgc.tarantino.crawler;

import org.ulpgc.tarantino.crawler.commands.IngestBookCommand;
import org.ulpgc.tarantino.crawler.commands.IngestResult;

import java.util.Arrays;

public class Main {

    public static void main(String[] args) {
        CrawlerConfig config = CrawlerConfig.fromEnvironment();
        CrawlerFactory.removeIncompleteWrites(config);
        IngestBookCommand ingest = CrawlerFactory.ingestCommand(config);
        Arrays.stream(args).map(Integer::parseInt).map(ingest::execute).map(Main::line).forEach(System.out::println);
    }

    /** One line per book, in the words of the control service (SPEC §14). */
    private static String line(IngestResult result) {
        return "[CRAWLER] " + result.bookId() + ": "
                + (result.succeeded() ? "stored in " + result.paths().body().getParent() : "skipped, " + result.failure());
    }
}
