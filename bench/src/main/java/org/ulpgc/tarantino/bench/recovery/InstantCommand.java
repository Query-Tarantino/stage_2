package org.ulpgc.tarantino.bench.recovery;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * {@code bench instant [<seconds from now>]}: the ISO-8601 UTC instant, to the millisecond, that a recovery script
 * passes as {@code failed_at} (SPEC §8). It comes from Java because {@code date} cannot add seconds or print
 * milliseconds the same way with GNU and BSD tools.
 */
public final class InstantCommand {

    private static final Pattern SECONDS = Pattern.compile("\\d+");

    private InstantCommand() {
    }

    /** Now, or that many whole seconds from now; nothing for any other arguments. */
    public static Optional<Instant> instant(List<String> args, Clock clock) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        if (args.isEmpty()) {
            return Optional.of(now);
        }
        if (args.size() == 1 && SECONDS.matcher(args.getFirst()).matches()) {
            return Optional.of(now.plusSeconds(Long.parseLong(args.getFirst())));
        }
        return Optional.empty();
    }
}
