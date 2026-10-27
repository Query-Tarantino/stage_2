package org.ulpgc.tarantino.query.ports;

import org.ulpgc.tarantino.query.model.BookDetails;
import org.ulpgc.tarantino.query.model.SearchResult;
import org.ulpgc.tarantino.query.model.Snapshot.BookFilter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@FunctionalInterface
public interface MetadataReader {

    /** The results of the given books, read with a single {@code {_id: {$in: page}}} query, in any order. */
    List<SearchResult> results(int generation, Collection<Integer> bookIds);

    /** A book's details from {@code metadata_g<n>} for {@code GET /books/{book_id}} (SPEC §6.4). */
    default Optional<BookDetails> bookDetails(int generation, int bookId) {
        return Optional.empty();
    }

    /** The filter metadata (languages and year) of the given books to compute {@code GET /search/filters} (SPEC §6.4). */
    default Map<Integer, BookFilter> filters(int generation, Collection<Integer> bookIds) {
        return Map.of();
    }

    /** The book ids matching the given metadata filters (SPEC §7). */
    default Set<Integer> matchingBookIds(int generation, Collection<String> authorTerms, String language, Integer year) {
        return Set.of();
    }
}
