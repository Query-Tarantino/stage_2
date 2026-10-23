package org.ulpgc.tarantino.query.commands;

import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.query.model.BookMetadata;
import org.ulpgc.tarantino.query.model.SearchResult;
import org.ulpgc.tarantino.query.ports.InvertedIndexReader;
import org.ulpgc.tarantino.query.ports.MetadataReader;

import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

public class SearchCommand {

    private final InvertedIndexReader invertedIndex;
    private final MetadataReader metadata;
    private final Tokenizer tokenizer;

    public SearchCommand(InvertedIndexReader invertedIndex, MetadataReader metadata, Tokenizer tokenizer) {
        this.invertedIndex = invertedIndex;
        this.metadata = metadata;
        this.tokenizer = tokenizer;
    }

    public SearchResult execute(String query) {
        return new SearchResult(query, booksContainingAll(tokenizer.distinctTerms(query)));
    }

    private List<BookMetadata> booksContainingAll(List<String> terms) {
        return idsContainingAll(terms).stream()
                .map(metadata::book)
                .flatMap(Optional::stream)
                .toList();
    }

    private TreeSet<Integer> idsContainingAll(List<String> terms) {
        return terms.stream()
                .map(term -> new TreeSet<>(invertedIndex.postings(term)))
                .reduce(SearchCommand::intersection)
                .orElseGet(TreeSet::new);
    }

    private static TreeSet<Integer> intersection(TreeSet<Integer> ids, TreeSet<Integer> others) {
        ids.retainAll(others);
        return ids;
    }
}
