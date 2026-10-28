package org.ulpgc.tarantino.bench.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import org.ulpgc.tarantino.common.model.BookStatus;
import org.ulpgc.tarantino.common.terms.Tokenizer;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * One pass of the checks of SPEC §8; it answers what failed, nothing once the run recovered. Until every seeded book is
 * {@code PUBLISHED} it checks nothing else. Then each book must answer at {@code /books/{book_id}} and come back
 * exactly once among every page of {@code /search} with the words of its title, and no book may be in two published
 * batches. Script 10 also waits {@code TARANTINO_GC_SECONDS} and then wants no posting of an abandoned batch.
 */
public class RecoveryPass {

    static final int PAGE = 100;

    private final Stack stack;
    private final RecoveryReads reads;
    private final Tokenizer tokenizer;
    private final Clock clock;
    private final Optional<Instant> abandonedPostingsGoneBy;

    public RecoveryPass(Stack stack, RecoveryReads reads, Tokenizer tokenizer, Clock clock, Optional<Instant> abandonedPostingsGoneBy) {
        this.stack = stack;
        this.reads = reads;
        this.tokenizer = tokenizer;
        this.clock = clock;
        this.abandonedPostingsGoneBy = abandonedPostingsGoneBy;
    }

    public List<String> failures() {
        List<Integer> seeded = reads.seededBooks();
        if (seeded.isEmpty()) {
            return List.of("No book is seeded yet");
        }
        List<String> unfinished = unfinishedBooks();
        if (!unfinished.isEmpty()) {
            return unfinished;
        }
        return Stream.of(seeded.stream().flatMap(bookId -> bookFailure(bookId).stream()),
                        duplicatedBooks().stream(), abandonedPostings().stream())
                .flatMap(failures -> failures)
                .toList();
    }

    private List<String> unfinishedBooks() {
        Answer status = stack.controlStatus();
        if (!status.ok()) {
            return List.of("GET /control/status answered " + status.status());
        }
        JsonNode books = status.body().path("books");
        return Arrays.stream(BookStatus.values())
                .filter(state -> state != BookStatus.PUBLISHED && books.path(state.name()).asInt() > 0)
                .map(state -> books.path(state.name()).asInt() + " books are still " + state.name())
                .toList();
    }

    private Optional<String> bookFailure(int bookId) {
        Answer book = stack.book(bookId);
        if (!book.ok()) {
            return Optional.of("GET /books/" + bookId + " answered " + book.status());
        }
        List<String> titleWords = tokenizer.distinctTerms(book.body().path("title").asText());
        if (titleWords.isEmpty()) {
            return Optional.empty();
        }
        return searchFailure(bookId, String.join(" ", titleWords));
    }

    private Optional<String> searchFailure(int bookId, String query) {
        long found = 0;
        int total = 1;
        for (int offset = 0; offset < total; offset += PAGE) {
            Answer page = stack.search(query, offset, PAGE);
            if (!page.ok()) {
                return Optional.of("/search?q=" + query + " answered " + page.status());
            }
            total = page.body().path("total").asInt();
            found += StreamSupport.stream(page.body().path("results").spliterator(), false)
                    .filter(result -> result.path("book_id").asInt() == bookId)
                    .count();
        }
        return found == 1 ? Optional.empty() : Optional.of("/search?q=" + query + " returned book " + bookId + " " + found + " times");
    }

    private Optional<String> duplicatedBooks() {
        List<Integer> duplicated = reads.booksInTwoPublishedBatches();
        return duplicated.isEmpty() ? Optional.empty() : Optional.of("Books in two published batches: " + duplicated);
    }

    private Optional<String> abandonedPostings() {
        if (abandonedPostingsGoneBy.isEmpty()) {
            return Optional.empty();
        }
        if (clock.instant().isBefore(abandonedPostingsGoneBy.get())) {
            return Optional.of("Waiting until " + abandonedPostingsGoneBy.get() + " for the postings of abandoned batches to go");
        }
        long left = reads.postingsOfAbandonedBatches();
        return left == 0 ? Optional.empty() : Optional.of(left + " postings of abandoned batches are left");
    }
}
