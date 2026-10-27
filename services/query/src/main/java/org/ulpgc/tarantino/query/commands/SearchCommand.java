package org.ulpgc.tarantino.query.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.terms.Tokenizer;
import org.ulpgc.tarantino.query.model.SearchQuery;
import org.ulpgc.tarantino.query.model.SearchResponse;
import org.ulpgc.tarantino.query.model.SearchResult;
import org.ulpgc.tarantino.query.model.Snapshot;
import org.ulpgc.tarantino.query.model.TermPostings;
import org.ulpgc.tarantino.query.ports.MetadataReader;
import org.ulpgc.tarantino.query.ports.PostingsReader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Executes a search over an immutable snapshot using TF-IDF ranking (SPEC §7). */
public class SearchCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchCommand.class);
    private static final Comparator<ScoredBook> RANKING = Comparator
            .comparingDouble(ScoredBook::score).reversed()
            .thenComparingInt(ScoredBook::bookId);

    private final PostingsReader postings;
    private final MetadataReader metadata;
    private final Tokenizer tokenizer;

    public SearchCommand(PostingsReader postings, MetadataReader metadata, Tokenizer tokenizer) {
        this.postings = postings;
        this.metadata = metadata;
        this.tokenizer = tokenizer;
    }

    public SearchResponse execute(Snapshot snapshot, SearchQuery query) {
        List<String> terms = tokenizer.distinctTerms(query.q());
        if (terms.isEmpty() || snapshot.bookCount() == 0) {
            return response(query, List.of(), List.of());
        }
        Set<Integer> filteredIds = null;
        if (query.hasFilters()) {
            filteredIds = new HashSet<>(metadata.matchingBookIds(
                    snapshot.generation(), query.authorTerms(), query.language(), query.year()));
            filteredIds.retainAll(snapshot.bookIds());
            if (filteredIds.isEmpty()) {
                return response(query, List.of(), List.of());
            }
        }
        Map<String, Map<Integer, Integer>> index = booksByTerm(
                postings.postings(snapshot.generation(), terms, snapshot.batchIds()));
        if (!index.keySet().containsAll(terms)) {
            return response(query, List.of(), List.of());
        }
        Set<Integer> matches = intersection(terms, index, filteredIds);
        if (matches.isEmpty()) {
            return response(query, List.of(), List.of());
        }
        List<ScoredBook> ranked = matches.stream()
                .map(bookId -> new ScoredBook(bookId, score(bookId, terms, index, snapshot.bookCount())))
                .sorted(RANKING)
                .toList();
        List<Integer> page = ranked.stream()
                .skip(query.offset())
                .limit(query.limit())
                .map(ScoredBook::bookId)
                .toList();
        List<SearchResult> pageResults = page.isEmpty()
                ? List.of()
                : inPageOrder(page, metadata.results(snapshot.generation(), page));
        return response(query, ranked, pageResults);
    }

    private static Map<String, Map<Integer, Integer>> booksByTerm(List<TermPostings> documents) {
        Map<String, Map<Integer, Integer>> index = new HashMap<>();
        for (TermPostings document : documents) {
            Map<Integer, Integer> books = index.computeIfAbsent(document.term(), key -> new HashMap<>());
            for (int i = 0; i < document.bookIds().size(); i++) {
                Integer bookId = document.bookIds().get(i);
                Integer previous = books.putIfAbsent(bookId, document.tfs().get(i));
                if (previous != null) {
                    LOGGER.error("Book {} appears twice in the postings of {}", bookId, document.term());
                }
            }
        }
        return index;
    }

    private static Set<Integer> intersection(
            List<String> terms, Map<String, Map<Integer, Integer>> index, Set<Integer> filteredIds) {
        List<Set<Integer>> candidateSets = new ArrayList<>(
                terms.stream().map(term -> index.get(term).keySet()).toList());
        if (filteredIds != null) {
            candidateSets.add(filteredIds);
        }
        candidateSets.sort(Comparator.comparingInt(Set::size));
        Set<Integer> matches = new HashSet<>(candidateSets.getFirst());
        candidateSets.subList(1, candidateSets.size()).forEach(matches::retainAll);
        return matches;
    }

    private static double score(
            int bookId, List<String> terms, Map<String, Map<Integer, Integer>> index, int totalBooks) {
        return terms.stream()
                .map(index::get)
                .mapToDouble(books -> (1 + Math.log(books.get(bookId))) * Math.log((double) totalBooks / books.size()))
                .sum();
    }

    private static List<SearchResult> inPageOrder(List<Integer> page, List<SearchResult> results) {
        Map<Integer, SearchResult> byBook = results.stream()
                .collect(Collectors.toMap(SearchResult::bookId, Function.identity()));
        return page.stream()
                .filter(byBook::containsKey)
                .map(byBook::get)
                .toList();
    }

    private static SearchResponse response(
            SearchQuery query, List<ScoredBook> ranked, List<SearchResult> pageResults) {
        return new SearchResponse(
                query.q(), query.filters(), ranked.size(), query.offset(), query.limit(), pageResults);
    }

    private record ScoredBook(int bookId, double score) {
    }
}