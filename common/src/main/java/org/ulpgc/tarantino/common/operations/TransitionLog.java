package org.ulpgc.tarantino.common.operations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/** Writes one INFO line per transition with the keys of SPEC §4.8 in their order, from which {@code bench} measures. */
public class TransitionLog {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransitionLog.class);
    private static final String NOT_APPLICABLE = "-";

    private final String replica;
    private final Clock clock;

    public TransitionLog() {
        this(hostname(), Clock.systemUTC());
    }

    TransitionLog(String replica, Clock clock) {
        this.replica = replica;
        this.clock = clock;
    }

    public void write(Transition transition) {
        LOGGER.info(line(transition));
    }

    String line(Transition transition) {
        return "transition entity=%s id=%s from=%s to=%s batch_id=%s generation=%s enqueued_at=%s at=%s replica=%s".formatted(
                transition.entity(), transition.id(), orNotApplicable(transition.from()), transition.to(),
                orNotApplicable(transition.batchId()), orNotApplicable(transition.generation()),
                orNotApplicable(transition.enqueuedAt()), clock.instant(), replica);
    }

    private static String orNotApplicable(Object value) {
        return Objects.toString(value, NOT_APPLICABLE);
    }

    private static String hostname() {
        return Optional.ofNullable(System.getenv("HOSTNAME")).orElseGet(TransitionLog::localHostName);
    }

    private static String localHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return NOT_APPLICABLE;
        }
    }
}
