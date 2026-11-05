package org.ulpgc.tarantino.control.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The books the orchestrator dispatched to one stage and not yet saw claimed (SPEC §11.4), each with the
 * {@code enqueued_at} it was dispatched with and the instant of its last dispatch. It lives in memory, so after a
 * {@code control} restart every waiting book is dispatched again; the claim decides, so a duplicate does no harm.
 */
public class DispatchedBooks {

    private final Map<Integer, Dispatch> books = new ConcurrentHashMap<>();

    public record Dispatch(int bookId, Instant enqueuedAt, Instant dispatchedAt) {
    }

    public void dispatched(int bookId, Instant enqueuedAt, Instant at) {
        books.put(bookId, new Dispatch(bookId, enqueuedAt, at));
    }

    public Set<Integer> bookIds() {
        return Set.copyOf(books.keySet());
    }

    public int size() {
        return books.size();
    }

    /** How many more books fit in a window of {@code window} books in flight. */
    public int room(int window) {
        return Math.max(0, window - books.size());
    }

    /** Drops the books that are no longer waiting for this stage. */
    public void keepOnly(Predicate<Integer> stillWaiting) {
        books.keySet().removeIf(stillWaiting.negate());
    }

    /** The books whose last dispatch is older than {@code requeue}, oldest first. */
    public List<Dispatch> dueAgain(Instant now, Duration requeue) {
        Instant before = now.minus(requeue);
        return books.values().stream()
                .filter(dispatch -> dispatch.dispatchedAt().isBefore(before))
                .sorted(Comparator.comparing(Dispatch::dispatchedAt).thenComparingInt(Dispatch::bookId))
                .toList();
    }
}
