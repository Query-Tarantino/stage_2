package org.ulpgc.tarantino.query.commands;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.query.model.BookMetadata;
import org.ulpgc.tarantino.query.model.SearchResult;
import org.ulpgc.tarantino.query.ports.InvertedIndexReader;
import org.ulpgc.tarantino.query.ports.MetadataReader;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchCommandTest {

    private static final Map<String, Set<Integer>> INDEX = Map.of(
            "island", Set.of(1342, 5, 84),
            "whale", Set.of(2701, 84, 5),
            "ghost", Set.of(999));

    private final InvertedIndexReader invertedIndex = term -> INDEX.getOrDefault(term, Set.of());
    private final MetadataReader metadata = new MetadataReader() {
        @Override
        public Optional<BookMetadata> book(int bookId) {
            return bookId == 999 ? Optional.empty() : Optional.of(metadataOf(bookId));
        }

        @Override
        public List<BookMetadata> booksBy(String author) {
            return List.of();
        }
    };
    private final SearchCommand search = new SearchCommand(invertedIndex, metadata, Set.of("the"));

    @Test
    void returnsBooksContainingEveryTermOrderedById() {
        assertEquals(List.of(5, 84), ids(search.execute("The WHALE island")));
    }

    @Test
    void returnsNothingWhenATermIsUnknown() {
        assertEquals(List.of(), ids(search.execute("island unicorn")));
    }

    @Test
    void returnsNothingWhenOnlyStopwordsRemain() {
        assertEquals(List.of(), ids(search.execute("the a")));
    }

    @Test
    void dropsBooksWithoutMetadata() {
        assertEquals(List.of(), ids(search.execute("ghost")));
    }

    private static List<Integer> ids(SearchResult result) {
        return result.books().stream().map(BookMetadata::bookId).toList();
    }

    private static BookMetadata metadataOf(int bookId) {
        return new BookMetadata(bookId, "Book " + bookId, "Author", "English", Path.of(bookId + ".body.txt"));
    }
}
