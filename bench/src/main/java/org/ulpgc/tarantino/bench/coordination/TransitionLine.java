package org.ulpgc.tarantino.bench.coordination;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A transition line of SPEC §4.8 read back from {@code docker compose logs}, whatever prefix the log puts in front.
 * {@code null} marks a {@code -}.
 */
public record TransitionLine(String entity, String id, String from, String to, String batchId, Instant enqueuedAt, Instant at) {

    public static final String BOOK = "book";
    public static final String BATCH = "batch";

    private static final String START = "transition entity=";
    private static final String KEY_VALUE = "=";
    private static final String NOT_APPLICABLE = "-";

    public static Optional<TransitionLine> parse(String logLine) {
        int start = logLine.indexOf(START);
        if (start < 0) {
            return Optional.empty();
        }
        Map<String, String> fields = Arrays.stream(logLine.substring(start).strip().split("\\s+"))
                .filter(field -> field.contains(KEY_VALUE))
                .collect(Collectors.toMap(field -> field.substring(0, field.indexOf(KEY_VALUE)),
                        field -> field.substring(field.indexOf(KEY_VALUE) + 1), (first, second) -> first));
        return Optional.of(new TransitionLine(fields.get("entity"), fields.get("id"), orNull(fields.get("from")),
                fields.get("to"), orNull(fields.get("batch_id")), instant(fields.get("enqueued_at")), instant(fields.get("at"))));
    }

    public boolean isBook() {
        return BOOK.equals(entity);
    }

    public boolean isBatch() {
        return BATCH.equals(entity);
    }

    public boolean moves(String fromState, String toState) {
        return fromState.equals(from) && toState.equals(to);
    }

    private static String orNull(String value) {
        return value == null || value.equals(NOT_APPLICABLE) ? null : value;
    }

    private static Instant instant(String value) {
        return orNull(value) == null ? null : Instant.parse(value);
    }
}
