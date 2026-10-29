package org.ulpgc.tarantino.bench.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryCommandTest {

    private static final Instant FAILED_AT = Instant.parse("2026-10-21T10:00:00Z");
    private static final Duration ONE_PASS = Duration.ofMillis(500);

    private final MovingClock clock = new MovingClock(FAILED_AT.plusSeconds(3));
    private final List<String> searched = new ArrayList<>();

    @TempDir
    Path runDirectory;

    @Test
    void endsAtTheFirstPassWhereEveryCheckHoldsAndKeepsTheStatusAndTheSearchAnswers() throws IOException {
        RecoveryCommand command = new RecoveryCommand(passes(List.of("1 books are still QUEUED"), List.of("boom"), List.of()),
                new AnsweringStack(), List.of("love", "old man sea fish"), clock, clock.pause());

        RecoveryChecks checks = command.awaitRecovery(FAILED_AT).orElseThrow();

        assertEquals(FAILED_AT.plusSeconds(3).plus(Duration.ofMillis(3_500)), checks.recoveredAt());
        assertEquals(6.5, checks.recoverySeconds());
        assertEquals(List.of("love", "old man sea fish"), searched);
        JsonNode written = TarantinoJson.mapper().readTree(Files.readString(checks.writeTo(runDirectory)));
        assertEquals(2680, written.get("control_status").get("books").get("PUBLISHED").asInt());
        assertEquals("old man sea fish", written.get("search").get("old man sea fish").get("query").asText());
        assertEquals("2026-10-21T10:00:00Z", written.get("failed_at").asText());
        assertEquals(6.5, written.get("recovery_seconds").asDouble());
    }

    @Test
    void keepsPollingThroughAPassWhereSomethingDidNotAnswer() {
        Supplier<List<String>> unreachableOnce = new Supplier<>() {
            private boolean answered;

            @Override
            public List<String> get() {
                if (!answered) {
                    answered = true;
                    throw new UncheckedIOException(new IOException("nginx is restarting"));
                }
                return List.of();
            }
        };

        assertTrue(new RecoveryCommand(unreachableOnce, new AnsweringStack(), List.of(), clock, clock.pause())
                .awaitRecovery(FAILED_AT).isPresent());
    }

    @Test
    void givesUpWhenTheRunDoesNotRecoverInTime() {
        RecoveryCommand command = new RecoveryCommand(() -> List.of("1 books are still FAILED"), new AnsweringStack(), List.of(),
                clock, clock.pause());

        assertTrue(command.awaitRecovery(FAILED_AT).isEmpty());
        assertEquals(FAILED_AT.plusSeconds(3).plus(RecoveryCommand.TIMEOUT), clock.instant());
    }

    @SafeVarargs
    private Supplier<List<String>> passes(List<String>... failuresOfEachPass) {
        Deque<List<String>> remaining = new ArrayDeque<>(List.of(failuresOfEachPass));
        return () -> {
            clock.advance(ONE_PASS);
            return remaining.size() > 1 ? remaining.removeFirst() : remaining.getFirst();
        };
    }

    private final class AnsweringStack implements Stack {

        @Override
        public Answer controlStatus() {
            return new Answer(200, TarantinoJson.mapper().valueToTree(Map.of("books", Map.of("PUBLISHED", 2680))));
        }

        @Override
        public Answer book(int bookId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Answer search(String query, int offset, int limit) {
            searched.add(query);
            return new Answer(200, TarantinoJson.mapper().valueToTree(Map.of("query", query, "limit", limit)));
        }
    }
}
