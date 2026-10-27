package org.ulpgc.tarantino.query.model;

import org.ulpgc.tarantino.common.language.LanguageCodes;
import org.ulpgc.tarantino.query.model.FiltersSummary.LanguageFilter;
import org.ulpgc.tarantino.query.model.FiltersSummary.YearRange;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What {@code query} can see (SPEC §7): the {@code PUBLISHED} batches of a generation, their books, and the
 * precalculated {@code /search/filters} summary. It is immutable, so a request reads it once and uses it whole.
 */
public record Snapshot(
        int generation,
        Set<String> batchIds,
        Set<Integer> bookIds,
        FiltersSummary filtersSummary,
        Map<Integer, BookFilter> bookFilters) {

    public Snapshot {
        batchIds = Set.copyOf(batchIds);
        bookIds = Set.copyOf(bookIds);
        bookFilters = Map.copyOf(bookFilters);
    }

    public Snapshot(int generation, Set<String> batchIds, Set<Integer> bookIds) {
        this(generation, batchIds, bookIds,
                new FiltersSummary(List.of(), new YearRange(null, null), bookIds.size()), Map.of());
    }

    public static Snapshot empty(int generation) {
        return new Snapshot(generation, Set.of(), Set.of(), FiltersSummary.empty(), Map.of());
    }

    /** {@code N} of TF-IDF: the books of the snapshot, never a count of the collection. */
    public int bookCount() {
        return bookIds.size();
    }

    /** The same snapshot plus some newly published batches and their books. */
    public Snapshot with(Map<String, List<Integer>> booksByBatch) {
        return with(booksByBatch, Map.of());
    }

    /** The same snapshot plus newly published batches, their books, and each book's filter metadata. */
    public Snapshot with(Map<String, List<Integer>> booksByBatch, Map<Integer, BookFilter> newFilters) {
        Set<String> batches = new HashSet<>(batchIds);
        batches.addAll(booksByBatch.keySet());
        Set<Integer> books = new HashSet<>(bookIds);
        booksByBatch.values().forEach(books::addAll);
        Map<Integer, BookFilter> combined = new HashMap<>(bookFilters);
        combined.putAll(newFilters);
        FiltersSummary summary = computeSummary(books.size(), combined.values());
        return new Snapshot(generation, batches, books, summary, combined);
    }

    private static FiltersSummary computeSummary(int totalBooks, Collection<BookFilter> filters) {
        if (totalBooks == 0) {
            return FiltersSummary.empty();
        }
        Map<String, Integer> languageCounts = new HashMap<>();
        for (BookFilter filter : filters) {
            for (String code : filter.languages()) {
                languageCounts.merge(code, 1, Integer::sum);
            }
        }
        List<LanguageFilter> languages = languageCounts.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry<String, Integer>::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> new LanguageFilter(entry.getKey(), LanguageCodes.name(entry.getKey()).orElse(null), entry.getValue()))
                .toList();
        IntSummaryStatistics yearStats = filters.stream()
                .map(BookFilter::year)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .summaryStatistics();
        YearRange years = yearStats.getCount() == 0
                ? new YearRange(null, null)
                : new YearRange(yearStats.getMin(), yearStats.getMax());
        return new FiltersSummary(languages, years, totalBooks);
    }

    public record BookFilter(List<String> languages, Integer year) {
        public BookFilter {
            languages = languages == null ? List.of() : List.copyOf(languages);
        }
    }
}
