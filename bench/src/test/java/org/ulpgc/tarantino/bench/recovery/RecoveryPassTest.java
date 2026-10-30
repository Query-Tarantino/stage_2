package org.ulpgc.tarantino.bench.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.json.TarantinoJson;
import org.ulpgc.tarantino.common.terms.Tokenizer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecoveryPassTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:01:00Z");
    private static final Tokenizer TOKENIZER = new Tokenizer(Set.of("and", "the", "of"));

    private final FakeStack stack = new FakeStack();
    private final FakeReads reads = new FakeReads();

    @Test
    void passesOnceEveryBookIsPublishedFoundOnceAndInOneBatch() {
        stack.published(84, "Frankenstein", List.of(84));
        stack.published(1342, "Pride and Prejudice", List.of(1342));

        assertEquals(List.of(), pass(Optional.empty()).failures());
    }

    @Test
    void checksNothingElseWhileABookIsNotPublishedYet() {
        stack.published(84, "Frankenstein", List.of(84));
        stack.counts.put("INDEXING", 1);

        assertEquals(List.of("1 books are still INDEXING"), pass(Optional.empty()).failures());
        assertEquals(List.of(), stack.searched);
    }

    @Test
    void failsUntilSomethingIsSeeded() {
        reads.seeded.clear();

        assertEquals(List.of("No book is seeded yet"), pass(Optional.empty()).failures());
    }

    @Test
    void walksEveryPageOfTheTitleSearchAndFailsOnABookFoundTwiceOrNotAtAll() {
        List<Integer> manyPoems = IntStream.rangeClosed(1, 150).boxed().collect(Collectors.toCollection(ArrayList::new));
        manyPoems.add(2701);
        stack.published(2701, "Poems", manyPoems);
        stack.published(84, "The Monster", List.of(84, 84));
        stack.published(11, "Alice", List.of());

        assertEquals(List.of(
                        "/search?q=alice returned book 11 0 times",
                        "/search?q=monster returned book 84 2 times"),
                pass(Optional.empty()).failures());
        assertEquals(List.of("alice@0", "monster@0", "poems@0", "poems@100"), stack.searched);
    }

    @Test
    void skipsTheSearchOfATitleWithOnlyStopwordsButNotItsBook() {
        stack.published(84, "The", List.of());
        stack.missing(1342);

        assertEquals(List.of("GET /books/1342 answered 404"), pass(Optional.empty()).failures());
    }

    @Test
    void searchesATitleOnlyWithTheWordsItsTextContains() {
        stack.published(3, "John Kennedy Inaugural Address", List.of());
        stack.results.put("inaugural address", List.of(3));
        reads.notInText.put(3, Set.of("john", "kennedy"));
        stack.published(5, "Kennedy", List.of());
        reads.notInText.put(5, Set.of("kennedy"));

        assertEquals(List.of(), pass(Optional.empty()).failures());
        assertEquals(List.of("inaugural address@0"), stack.searched);
    }

    @Test
    void failsOnABookInTwoPublishedBatches() {
        stack.published(84, "Frankenstein", List.of(84));
        reads.duplicated.add(84);

        assertEquals(List.of("Books in two published batches: [84]"), pass(Optional.empty()).failures());
    }

    @Test
    void waitsForTheCollectionOfAbandonedPostingsBeforeCountingThem() {
        stack.published(84, "Frankenstein", List.of(84));
        reads.abandonedPostings = 3;

        assertEquals(List.of("Waiting until 2026-10-21T10:01:30Z for the postings of abandoned batches to go"),
                pass(Optional.of(NOW.plusSeconds(30))).failures());
        assertEquals(List.of("3 postings of abandoned batches are left"), pass(Optional.of(NOW)).failures());

        reads.abandonedPostings = 0;
        assertEquals(List.of(), pass(Optional.of(NOW)).failures());
    }

    private RecoveryPass pass(Optional<Instant> abandonedPostingsGoneBy) {
        return new RecoveryPass(stack, reads, TOKENIZER, Clock.fixed(NOW, ZoneOffset.UTC), abandonedPostingsGoneBy);
    }

    private final class FakeStack implements Stack {

        final Map<String, Integer> counts = new HashMap<>(Map.of("PUBLISHED", 0));
        final Map<Integer, String> titles = new HashMap<>();
        final Map<String, List<Integer>> results = new HashMap<>();
        final List<String> searched = new ArrayList<>();

        void published(int bookId, String title, List<Integer> titleResults) {
            reads.seeded.add(bookId);
            reads.seeded.sort(Integer::compare);
            counts.merge("PUBLISHED", 1, Integer::sum);
            titles.put(bookId, title);
            results.put(String.join(" ", TOKENIZER.distinctTerms(title)), titleResults);
        }

        void missing(int bookId) {
            reads.seeded.add(bookId);
            counts.merge("PUBLISHED", 1, Integer::sum);
        }

        @Override
        public Answer controlStatus() {
            return new Answer(200, json(Map.of("books", counts)));
        }

        @Override
        public Answer book(int bookId) {
            return titles.containsKey(bookId) ? new Answer(200, json(Map.of("book_id", bookId, "title", titles.get(bookId))))
                    : new Answer(404, json(Map.of("status", 404)));
        }

        @Override
        public Answer search(String query, int offset, int limit) {
            searched.add(query + "@" + offset);
            List<Integer> all = results.getOrDefault(query, List.of());
            List<Map<String, Integer>> page = all.subList(Math.min(offset, all.size()), Math.min(offset + limit, all.size())).stream()
                    .map(bookId -> Map.of("book_id", bookId))
                    .toList();
            return new Answer(200, json(Map.of("total", all.size(), "results", page)));
        }

        private JsonNode json(Object value) {
            return TarantinoJson.mapper().valueToTree(value);
        }
    }

    private static final class FakeReads implements RecoveryReads {

        final List<Integer> seeded = new ArrayList<>();
        final List<Integer> duplicated = new ArrayList<>();
        final Map<Integer, Set<String>> notInText = new HashMap<>();
        long abandonedPostings;

        @Override
        public List<Integer> seededBooks() {
            return seeded;
        }

        @Override
        public List<Integer> booksInTwoPublishedBatches() {
            return duplicated;
        }

        @Override
        public List<String> termsInText(int bookId, List<String> terms) {
            return terms.stream().filter(term -> !notInText.getOrDefault(bookId, Set.of()).contains(term)).toList();
        }

        @Override
        public long postingsOfAbandonedBatches() {
            return abandonedPostings;
        }
    }
}
