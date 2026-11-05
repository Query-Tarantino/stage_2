package org.ulpgc.tarantino.bench.recovery;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Script 14 (SPEC §8): once recovered, {@code /search} every 100 ms for 60 s, going through the queries in turn, while
 * a {@code query} replica is killed. Any answer other than {@code 200}, or none, fails the run.
 */
public class ContinuousSearch {

    static final Duration EVERY = Duration.ofMillis(100);
    static final Duration DURING = Duration.ofSeconds(60);

    private final Stack stack;
    private final List<String> queries;
    private final Clock clock;
    private final Pause pause;

    public ContinuousSearch(Stack stack, List<String> queries, Clock clock, Pause pause) {
        this.stack = stack;
        this.queries = List.copyOf(queries);
        this.clock = clock;
        this.pause = pause;
    }

    /** The first search that did not answer {@code 200}, if any. */
    public Optional<String> failure() {
        Instant end = clock.instant().plus(DURING);
        for (int request = 0; clock.instant().isBefore(end); request++) {
            String query = queries.get(request % queries.size());
            Optional<String> failure = failureOf(query);
            if (failure.isPresent()) {
                return failure;
            }
            pause.sleep(EVERY);
        }
        return Optional.empty();
    }

    private Optional<String> failureOf(String query) {
        try {
            Answer answer = stack.search(query, 0, RecoveryCommand.DEFAULT_SEARCH_LIMIT);
            return answer.ok() ? Optional.empty() : Optional.of("/search?q=" + query + " answered " + answer.status() + " at " + clock.instant());
        } catch (RuntimeException e) {
            return Optional.of("/search?q=" + query + " got no answer at " + clock.instant() + ": " + e);
        }
    }
}
