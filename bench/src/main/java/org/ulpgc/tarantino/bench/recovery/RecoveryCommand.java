package org.ulpgc.tarantino.bench.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code bench recovery} (SPEC §8): runs a pass of the checks every second until one holds entirely, and then reads
 * what {@code checks.json} keeps. The recovery time goes from the failure to the end of that pass. It gives up after
 * {@link #TIMEOUT}, so a run that never recovers fails instead of hanging.
 */
public class RecoveryCommand {

    static final Duration POLL = Duration.ofSeconds(1);
    static final Duration TIMEOUT = Duration.ofMinutes(15);
    static final int DEFAULT_SEARCH_LIMIT = 20;

    private static final Logger LOGGER = LoggerFactory.getLogger(RecoveryCommand.class);
    private static final int FAILURES_LOGGED = 5;
    private static final double MILLIS_PER_SECOND = 1_000.0;

    private final Supplier<List<String>> pass;
    private final Stack stack;
    private final List<String> queries;
    private final Clock clock;
    private final Pause pause;

    public RecoveryCommand(Supplier<List<String>> pass, Stack stack, List<String> queries, Clock clock, Pause pause) {
        this.pass = pass;
        this.stack = stack;
        this.queries = List.copyOf(queries);
        this.clock = clock;
        this.pause = pause;
    }

    /** The checks of the run once it recovered, or nothing if it did not within {@link #TIMEOUT}. */
    public Optional<RecoveryChecks> awaitRecovery(Instant failedAt) {
        Instant deadline = clock.instant().plus(TIMEOUT);
        while (true) {
            List<String> failures = failuresOfOnePass();
            Instant passEnd = clock.instant();
            if (failures.isEmpty()) {
                return Optional.of(checks(failedAt, passEnd));
            }
            if (!passEnd.isBefore(deadline)) {
                LOGGER.error("Not recovered after {}: {}", TIMEOUT, failures);
                return Optional.empty();
            }
            LOGGER.info("Not recovered yet, {} checks fail: {}", failures.size(), failures.stream().limit(FAILURES_LOGGED).toList());
            pause.sleep(POLL);
        }
    }

    private List<String> failuresOfOnePass() {
        try {
            return pass.get();
        } catch (RuntimeException e) {
            return List.of("A check got no answer: " + e);
        }
    }

    private RecoveryChecks checks(Instant failedAt, Instant recoveredAt) {
        Map<String, JsonNode> answers = new LinkedHashMap<>();
        queries.forEach(query -> answers.put(query, stack.search(query, 0, DEFAULT_SEARCH_LIMIT).body()));
        double recoverySeconds = Duration.between(failedAt, recoveredAt).toMillis() / MILLIS_PER_SECOND;
        return new RecoveryChecks(stack.controlStatus().body(), answers, failedAt, recoveredAt, recoverySeconds);
    }
}
