package org.ulpgc.tarantino.common.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** The coordination modes of SPEC §11, selected with {@code TARANTINO_COORDINATION}. */
public enum Coordination {
    HAZELCAST(true),
    ACTIVEMQ(true),
    POLLING(false),
    ORCHESTRATOR(false);

    private final boolean usesBroker;

    Coordination(boolean usesBroker) {
        this.usesBroker = usesBroker;
    }

    /** Whether work and announcements travel through a message broker (SPEC §11.1). */
    public boolean usesBroker() {
        return usesBroker;
    }

    /** The value of {@code TARANTINO_COORDINATION}, its name in lower case. */
    public String variableValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<Coordination> of(String value) {
        return Arrays.stream(values()).filter(mode -> mode.variableValue().equals(value)).findFirst();
    }
}
