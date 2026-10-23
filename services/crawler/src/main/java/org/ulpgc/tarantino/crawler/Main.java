package org.ulpgc.tarantino.crawler;

import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.crawler.commands.IngestBookCommand;
import org.ulpgc.tarantino.crawler.commands.IngestResult;

import java.util.Arrays;

public class Main {

    public static void main(String[] args) {
        IngestBookCommand ingest = CrawlerFactory.ingestCommand(TarantinoConfig.fromEnvironment(TarantinoService.CRAWLER));
        Arrays.stream(args).map(Integer::parseInt).map(ingest::execute).map(Main::line).forEach(System.out::println);
    }

    private static String line(IngestResult result) {
        return "[CRAWLER] " + result.bookId() + ": "
                + (result.succeeded() ? "stored in " + result.paths().body().getParent() : "skipped, " + result.failure());
    }
}
