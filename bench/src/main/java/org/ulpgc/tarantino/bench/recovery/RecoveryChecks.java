package org.ulpgc.tarantino.bench.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

/**
 * The {@code checks.json} of a recovery run (SPEC §9): {@code GET /control/status} and the {@code /search} answer to
 * each query of {@code workload/queries.txt} once recovered, with the failure, the recovery and the time between them.
 */
public record RecoveryChecks(JsonNode controlStatus, Map<String, JsonNode> search, Instant failedAt, Instant recoveredAt,
                             double recoverySeconds) {

    public static final String FILE_NAME = "checks.json";

    public Path writeTo(Path runDirectory) {
        try {
            String json = TarantinoJson.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(this);
            return Files.writeString(runDirectory.resolve(FILE_NAME), json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
