package org.ulpgc.tarantino.bench.recovery;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContinuousSearchTest {

    private static final Instant START = Instant.parse("2026-10-21T10:00:00Z");

    private final MovingClock clock = new MovingClock(START);
    private final List<String> searched = new ArrayList<>();

    @Test
    void searchesEveryHundredMillisecondsForAMinuteGoingThroughTheQueries() {
        Optional<String> failure = search(request -> 200).failure();

        assertEquals(Optional.empty(), failure);
        assertEquals(600, searched.size());
        assertEquals(List.of("love", "night", "love"), searched.subList(0, 3));
    }

    @Test
    void stopsAtTheFirstAnswerOtherThanOk() {
        Optional<String> failure = search(request -> request == 200 ? 503 : 200).failure();

        assertEquals(Optional.of("/search?q=love answered 503 at 2026-10-21T10:00:20Z"), failure);
    }

    @Test
    void failsWhenASearchGetsNoAnswer() {
        Optional<String> failure = search(request -> -1).failure();

        assertEquals(Optional.of("/search?q=love got no answer at 2026-10-21T10:00:00Z: java.io.UncheckedIOException: java.io.IOException: refused"),
                failure);
    }

    private ContinuousSearch search(IntFunction<Integer> statusOfRequest) {
        Stack stack = new Stack() {
            @Override
            public Answer controlStatus() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Answer book(int bookId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Answer search(String query, int offset, int limit) {
                int status = statusOfRequest.apply(searched.size());
                searched.add(query);
                if (status < 0) {
                    throw new UncheckedIOException(new IOException("refused"));
                }
                return new Answer(status, TarantinoJson.mapper().missingNode());
            }
        };
        return new ContinuousSearch(stack, List.of("love", "night"), clock, clock.pause());
    }
}
