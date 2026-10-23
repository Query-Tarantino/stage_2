package org.ulpgc.tarantino.control.adapters;

import org.ulpgc.tarantino.control.model.Outcome;
import org.ulpgc.tarantino.control.ports.Indexer;
import org.ulpgc.tarantino.indexer.commands.IndexBookCommand;
import org.ulpgc.tarantino.indexer.commands.IndexResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LocalIndexer implements Indexer {

    private final IndexBookCommand index;

    public LocalIndexer(IndexBookCommand index) {
        this.index = index;
    }

    @Override
    public Map<Integer, Outcome> index(List<Integer> bookIds) {
        Map<Integer, Outcome> outcomes = new LinkedHashMap<>();
        index.execute(bookIds).forEach(result -> outcomes.put(result.bookId(), outcome(result)));
        return outcomes;
    }

    private static Outcome outcome(IndexResult result) {
        return result.indexed()
                ? Outcome.success(result.uniqueTerms() + " unique terms indexed")
                : Outcome.failure("skipped, not found in the datalake");
    }
}
