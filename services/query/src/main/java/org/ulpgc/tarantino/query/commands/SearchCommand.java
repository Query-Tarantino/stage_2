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

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A {@code GET /search} over one snapshot (SPEC §7): the books that contain every term of {@code q}, by descending
 * TF-IDF, {@code (1 + ln tf) × ln(N / df)} per term, and ascending {@code book_id}.
 */
public class SearchCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchCommand.class);
    private static final Comparator<ScoredBook> RANKING =
            Comparator.comparingDouble(ScoredBook::score).reversed().thenComparingInt(ScoredBook::bookId);

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
        Map<String, Map<Integer, Integer>> booksByTerm = booksByTerm(postings.postings(snapshot.generation(), terms, snapshot.batchIds()));
        if (!booksByTerm.keySet().containsAll(terms)) {
            return response(query, List.of(), List.of());
        }
        List<ScoredBook> ranked = intersection(terms, booksByTerm).stream()
                .map(bookId -> new ScoredBook(bookId, score(bookId, booksByTerm, snapshot.bookCount())))
                .sorted(RANKING)
                .toList();
        List<Integer> page = ranked.stream().skip(query.offset()).limit(query.limit()).map(ScoredBook::bookId).toList();
        return response(query, ranked, inPageOrder(page, metadata.results(snapshot.generation(), page)));
    }

    /** Term to book to {@code tf}; a book repeated within a term counts once and is logged as an error (SPEC §5.2). */
    private static Map<String, Map<Integer, Integer>> booksByTerm(List<TermPostings> documents) {
        Map<String, Map<Integer, Integer>> booksByTerm = new HashMap<>();
        for (TermPostings document : documents) {
            Map<Integer, Integer> books = booksByTerm.computeIfAbsent(document.term(), term -> new HashMap<>());
            for (int index = 0; index < document.bookIds().size(); index++) {
                if (books.putIfAbsent(document.bookIds().get(index), document.tfs().get(index)) != null) {
                    LOGGER.error("Book {} appears twice in the postings of {}", document.bookIds().get(index), document.term());
                }
            }
        }
        return booksByTerm;
    }

    private static Set<Integer> intersection(List<String> terms, Map<String, Map<Integer, Integer>> booksByTerm) {
        List<Set<Integer>> booksOfEachTerm = terms.stream()
                .map(term -> booksByTerm.get(term).keySet())
                .sorted(Comparator.comparingInt(Set::size))
                .toList();
        Set<Integer> matches = new HashSet<>(booksOfEachTerm.getFirst());
        booksOfEachTerm.stream().skip(1).forEach(matches::retainAll);
        return matches;
    }

    private static double score(int bookId, Map<String, Map<Integer, Integer>> booksByTerm, int bookCount) {
        return booksByTerm.values().stream()
                .mapToDouble(books -> (1 + Math.log(books.get(bookId))) * Math.log((double) bookCount / books.size()))
                .sum();
    }

    private static List<SearchResult> inPageOrder(List<Integer> page, List<SearchResult> results) {
        Map<Integer, SearchResult> byBook = results.stream().collect(Collectors.toMap(SearchResult::bookId, Function.identity()));
        return page.stream().filter(byBook::containsKey).map(byBook::get).toList();
    }

    private static SearchResponse response(SearchQuery query, List<ScoredBook> matches, List<SearchResult> results) {
        return new SearchResponse(query.q(), Map.of(), matches.size(), query.offset(), query.limit(), results);
    }

    private record ScoredBook(int bookId, double score) {
    }
}
