package org.ulpgc.tarantino.indexer.model.terms;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Groups the term frequencies of a batch's books into one {@link TermPostings} per term, in term order. */
public final class BatchPostings {

    private BatchPostings() {
    }

    public static List<TermPostings> of(Map<Integer, Map<String, Integer>> frequenciesByBook) {
        SortedMap<String, SortedMap<Integer, Integer>> booksByTerm = frequenciesByBook.entrySet().stream()
                .flatMap(book -> book.getValue().entrySet().stream()
                        .map(term -> new Occurrence(term.getKey(), book.getKey(), term.getValue())))
                .collect(Collectors.groupingBy(Occurrence::term, TreeMap::new,
                        Collectors.toMap(Occurrence::bookId, Occurrence::tf, Integer::sum, TreeMap::new)));
        return booksByTerm.entrySet().stream()
                .map(term -> new TermPostings(term.getKey(), List.copyOf(term.getValue().keySet()), List.copyOf(term.getValue().values())))
                .toList();
    }

    private record Occurrence(String term, int bookId, int tf) {
    }
}
