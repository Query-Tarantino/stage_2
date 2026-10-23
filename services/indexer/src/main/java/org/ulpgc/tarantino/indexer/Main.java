package org.ulpgc.tarantino.indexer;

import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.indexer.commands.IndexBookCommand;
import org.ulpgc.tarantino.indexer.commands.IndexResult;

import java.util.Arrays;

public class Main {

    public static void main(String[] args) {
        IndexBookCommand index = IndexerFactory.indexCommand(TarantinoConfig.fromEnvironment(TarantinoService.INDEXER));
        index.execute(Arrays.stream(args).map(Integer::valueOf).toList()).stream().map(Main::line).forEach(System.out::println);
    }

    private static String line(IndexResult result) {
        return "[INDEXER] " + result.bookId() + ": "
                + (result.indexed() ? result.uniqueTerms() + " unique terms indexed" : "skipped, not found in the datalake");
    }
}
