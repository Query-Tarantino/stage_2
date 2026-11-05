package org.ulpgc.tarantino.bench.visibility;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** A {@code snapshot} line of SPEC §4.8 read back from {@code docker compose logs}, whatever prefix the log puts in front. */
public record SnapshotLine(String batchId, int generation, Instant at, String replica) {

    private static final String START = "snapshot batch_id=";
    private static final String KEY_VALUE = "=";

    public static Optional<SnapshotLine> parse(String logLine) {
        int start = logLine.indexOf(START);
        if (start < 0) {
            return Optional.empty();
        }
        Map<String, String> fields = Arrays.stream(logLine.substring(start).strip().split("\\s+"))
                .filter(field -> field.contains(KEY_VALUE))
                .collect(Collectors.toMap(field -> field.substring(0, field.indexOf(KEY_VALUE)),
                        field -> field.substring(field.indexOf(KEY_VALUE) + 1), (first, second) -> first));
        return Optional.of(new SnapshotLine(fields.get("batch_id"), Integer.parseInt(fields.get("generation")),
                Instant.parse(fields.get("at")), fields.get("replica")));
    }
}
